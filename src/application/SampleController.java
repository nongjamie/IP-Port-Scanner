package application;

import java.util.Collections;
import java.util.Comparator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import util.NetworkScanner;

public class SampleController {
	@FXML
	private Button Play;
	@FXML
	private ListView<DisplayResult> view;
	@FXML
	private ListView<Integer> view2;
	@FXML
	private Button Stop;
	private Task<Void> scan_thread;
	private NetworkScanner scanner;
	@FXML
	private Button Pause;
	@FXML
	private ProgressBar bar;

	@FXML
	public void handleMouseClick(MouseEvent arg0) {
		System.out.println("clicked on " + view.getSelectionModel().getSelectedItem());
		System.out.println(view.getSelectionModel().getSelectedItem().getClass());
		ObservableList<Integer> list = FXCollections.observableArrayList();
		DisplayResult displayPort = view.getSelectionModel().getSelectedItem();
		if (displayPort != null) {
			view2.getItems().clear();
			for (Integer port : displayPort.getPort()) {
				list.add(port);
			}
			Collections.sort(list, portOrder);
			view2.setItems(list);
			view2.setCellFactory(new Callback<ListView<Integer>, ListCell<Integer>>() {
				@Override
				public ListCell<Integer> call(ListView<Integer> list) {
					return new UpdatePort();
				}
			});
		}

	}

	public void play(ActionEvent e) {
		System.out.println("varit");
		scan_thread = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				scanner.scan();
				return null;
			}
		};
		bar.progressProperty().bind(scan_thread.progressProperty());
		new Thread(scan_thread).start();
	}

	public void setNetworkScanner(NetworkScanner scanner) {
		this.scanner = scanner;
	}

	public void show(NetworkObserver obs) {
		view.setItems(obs.getList());
		view.setCellFactory(new Callback<ListView<DisplayResult>, ListCell<DisplayResult>>() {
			@Override
			public ListCell<DisplayResult> call(ListView<DisplayResult> list) {
				return new Update();
			}
		});
	}

	public void pause(ActionEvent e) {
		System.out.println("Pause");
	}

	public void stop(ActionEvent e) {
		scanner.stop();

	}

	private static class Update extends ListCell<DisplayResult> {
		@Override
		public void updateItem(DisplayResult item, boolean empty) {
			super.updateItem(item, empty);
			if (item != null) {
				setText(item.getIp());
			}
		}
	}

	private static class UpdatePort extends ListCell<Integer> {
		@Override
		public void updateItem(Integer item, boolean empty) {
			super.updateItem(item, empty);
			if (item != null) {
				setText(item + "");
			}
		}
	}

	Comparator<Integer> portOrder = new Comparator<Integer>() {
		@Override
		public int compare(Integer m1, Integer m2) {
			return m1.compareTo(m2);
		}
	};

}
