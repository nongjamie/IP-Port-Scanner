package util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.javafx.PlatformUtil;

public class NetworkScanner extends Observable {

	private boolean isRunning;
	private List<Future<ScanResult>> futures;

	public NetworkScanner() {
		this.isRunning = false;
	}

	public void scan(String fromIP, String toIP, int fromPort, int toPort) {

		int aliveIps = 0;
		int deadIps = 0;
		int openPorts = 0;
		try {
			this.isRunning = true;
			final ExecutorService es = Executors.newFixedThreadPool(500);
			int timeout = 100;
			futures = new ArrayList<>();
			List<String> ipRange = getIpList(fromIP, toIP);
			for (String ipaddr : ipRange) {
				if (!isRunning)
					break;
				if (isReachable(ipaddr)) {
					aliveIps++;
					String ping = ping(ipaddr);
					for (int port = fromPort; port <= toPort; port++) {
						if (!isRunning)
							break;
						futures.add(portIsOpen(es, ipaddr, port, timeout, ping));
					}
				} else {
					deadIps++;
					System.out.println(ipaddr + " is not reachable");
				}
			}
			es.awaitTermination(200L, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// do nothing.
		}

		for (final Future<ScanResult> f : futures) {
			try {
				if (f.get().isOpen()) {
					openPorts++;
				}
			} catch (InterruptedException e) {
			} catch (ExecutionException e) {
			}
		}
		/* send Summary to UI */
		setChanged();
		notifyObservers(new Summary(openPorts, (toPort - fromPort + 1) - openPorts, deadIps, aliveIps));
	}

	public void scanByDomainName(String domainName, int fromPort, int toPort) {
		int aliveIps = 0;
		int deadIps = 0;
		int openPorts = 0;
		try {
			this.isRunning = true;
			final ExecutorService es = Executors.newFixedThreadPool(500);
			int timeout = 100;
			futures = new ArrayList<>();
			if (isReachable(domainName)) {
				String ping = ping(domainName);
				aliveIps++;
				for (int port = fromPort; port <= toPort; port++) {
					if (!isRunning)
						break;
					futures.add(portIsOpen(es, domainName, port, timeout, ping));
				}
			} else {
				deadIps++;
				System.out.println(domainName + " is not reachable");
			}

			es.awaitTermination(200L, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {

		}

		for (final Future<ScanResult> f : futures) {
			try {
				if (f.get().isOpen()) {
					openPorts++;
				}
			} catch (InterruptedException e) {
			} catch (ExecutionException e) {
			}
		}
		/* send Summary to UI */
		setChanged();
		notifyObservers(new Summary(openPorts, (futures.size()) - openPorts, deadIps, aliveIps));
	}

	public void stop() {
		this.isRunning = false;
		for (final Future<ScanResult> f : futures) {
			f.cancel(true);
		}
	}

	private boolean isReachable(String host) {
		try {
			return InetAddress.getByName(host).isReachable(2000);
		} catch (Exception e) {
			return false;
		}
	}

	public String dnsLookup(String domain) {
		try {
			InetAddress address = InetAddress.getByName(getUrlDomainName(domain));
			if (address.isReachable(2000)) {
				if (isIPv4(domain))
					return address.getHostName();
				return address.getHostAddress();
			}
		} catch (Exception e) {
			System.out.println("EXCEPTION");
			return "Can't resolve hostname";
		}
		return "Can't resolve hostname";
	}

	private String getUrlDomainName(String url) {
		String domainName = new String(url);

		int index = domainName.indexOf("://");

		if (index != -1) {
			// keep everything after the "://"
			domainName = domainName.substring(index + 3);
		}

		index = domainName.indexOf('/');

		if (index != -1) {
			// keep everything before the '/'
			domainName = domainName.substring(0, index);
		}

		// check for and remove a preceding 'www'
		// followed by any sequence of characters (non-greedy)
		// followed by a '.'
		// from the beginning of the string
		domainName = domainName.replaceFirst("^www.*?\\.", "");
		System.out.println(domainName);
		return domainName;
	}

	private String ping(String host) {
		PingStrategy pingStategy;
		if (PlatformUtil.isWindows()) {
			pingStategy = new PingWIndows(host);
		} else {
			pingStategy = new PingMacOS(host);
		}
		return pingStategy.ping();
	}

	private Future<ScanResult> portIsOpen(final ExecutorService es, final String ip, final int port, final int timeout,
			String ping) {
		return es.submit(new Callable<ScanResult>() {
			@Override
			public ScanResult call() {
				try {
					Socket socket = new Socket();
					socket.connect(new InetSocketAddress(ip, port), timeout);
					socket.close();
					/* send Scan Result to UI */
					setChanged();
					notifyObservers(new ScanResult(ip, port, true, ping));
					return new ScanResult(ip, port, true, ping);
				} catch (Exception ex) {/* send Scan Result to UI */
					setChanged();
					notifyObservers(new ScanResult(ip, port, false, ping));
					return new ScanResult(ip, port, false, ping);
				}
			}
		});
	}

	private static boolean isIPv4(final String ip) {
		String PATTERN = "^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$";
		return ip.matches(PATTERN);
	}

	private final String nextIpAddress(final String input) {
		final String[] tokens = input.split("\\.");
		if (tokens.length != 4)
			throw new IllegalArgumentException();
		for (int i = tokens.length - 1; i >= 0; i--) {
			final int item = Integer.parseInt(tokens[i]);
			if (item < 255) {
				tokens[i] = String.valueOf(item + 1);
				for (int j = i + 1; j < 4; j++) {
					tokens[j] = "0";
				}
				break;
			}
		}
		return new StringBuilder().append(tokens[0]).append('.').append(tokens[1]).append('.').append(tokens[2])
				.append('.').append(tokens[3]).toString();
	}

	private List<String> getIpList(String startIp, String endIp) {
		List<String> result = new ArrayList<>();
		String currentIp = startIp;
		result.add(startIp);
		while (!currentIp.equals(endIp)) {
			String nextIp = nextIpAddress(currentIp);
			result.add(nextIp);
			currentIp = nextIp;
		}
		return result;
	}

}
