package server;

import cli.Command;
import cli.Shell;
import message.response.MessageResponse;
import util.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.MissingResourceException;
import java.util.Timer;

public class FileServerImpl {

	private Shell shell;
	private Config config;

	private Timer timer;

	private ServerSocket socket;

	public FileServerImpl(Shell shell, Config config) {
		this.shell = shell;
		this.config = config;
	}

	public IFileServerCli start() throws IOException {
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
			return null;
		}

		socket = new ServerSocket(port);
		ClientThread.initNewThread(socket, dir);

		AliveTask task = new AliveTask(proxyHost, proxyPort, port);
		timer = new Timer("alive");
		timer.schedule(task, 0, aliveInterval);

		FileServerCommands fileServerCommands = new FileServerCommands();
		shell.register(fileServerCommands);

		return fileServerCommands;
	}

	public static void main(String[] args) throws IOException {
		assert args.length >= 1: "Gimme config";
		Config config = new Config(args[0]);

		Shell shell = new Shell("FS", System.out, System.in);

		FileServerImpl fs = new FileServerImpl(shell, config);
		fs.start();
		shell.run();
	}

	private void stop() throws IOException {
		timer.cancel();
		shell.close();
		System.in.close();
	}

	private class FileServerCommands implements IFileServerCli {
		@Override
		@Command
		public MessageResponse exit() throws IOException {
			stop();
			return new MessageResponse("FileServer shutting down");
		}
	}
}
