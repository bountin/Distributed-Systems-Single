package server;

import util.Config;

import java.util.MissingResourceException;
import java.util.Timer;

public class FileServerImpl {

	public static void main(String[] args) {
		assert args.length >= 1: "Gimme config";

		Config config = new Config(args[0]);

		String dir, proxyHost;
		int port, proxyPort, aliveInterval;

		try {
			dir = config.getString("fileserver.dir");
			port = config.getInt("tcp.port");
			proxyHost = config.getString("proxy.host");
			proxyPort = config.getInt("proxy.udp.port");
			aliveInterval = config.getInt("fileserver.alive");
		} catch (MissingResourceException e) {
			System.out.println("A configuration parameter is missing: " + e.getMessage());
			System.exit(1);
			return;
		}

		AliveTask task = new AliveTask(proxyHost, proxyPort, port);
		Timer timer = new Timer("alive");
		timer.schedule(task, 0, aliveInterval);
	}
}
