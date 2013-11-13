package client;

import cli.Command;
import cli.Shell;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.LoginRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.LoginResponse;
import message.response.MessageResponse;
import util.Config;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.MissingResourceException;

public class ClientImpl {
	private static Shell shell;
	private static String host;
	private static String downloadDir;
	private static int port;

	private static ObjectOutputStream out;
	private static ObjectInputStream in;

	public static void main(String[] args) throws IOException {
		Config config = new Config("client");

		try {
			downloadDir = config.getString("download.dir");
			host = config.getString("proxy.host");
			port = config.getInt("proxy.tcp.port");
		} catch (MissingResourceException e) {
			System.out.println("A configuration parameter is missing: " + e.getMessage());
			System.exit(1);
			return;
		}

		Socket socket = new Socket((String) null, port);
		out = new ObjectOutputStream(socket.getOutputStream());
		out.flush();
		in = new ObjectInputStream(socket.getInputStream());

		shell = new Shell("Client", System.out, System.in);
		shell.register(new ClientCommands());
		shell.run();
	}

	static class ClientCommands implements IClientCli {

		private boolean loggedIn = false;

		@Override
		@Command
		public LoginResponse login(String username, String password) throws IOException {
			LoginRequest request = new LoginRequest(username, password);
			out.writeObject(request);
			out.flush();

			Object response = null;
			try {
				response = in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

			if (response instanceof LoginResponse) {
				LoginResponse loginResponse = (LoginResponse) response;
				loggedIn = loginResponse.getType() == LoginResponse.Type.SUCCESS;
				return loginResponse;
			} else {
				return null;
			}
		}

		@Override
		@Command
		public Response credits() throws IOException {
			if (!checkLoginStatus())
				return null;

			CreditsRequest request = new CreditsRequest();
			out.writeObject(request);
			out.flush();

			Object response = null;
			try {
				response = in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

			if (response instanceof CreditsResponse) {
				return (CreditsResponse) response;
			}

			return null;
		}

		@Override
		@Command
		public Response buy(long credits) throws IOException {
			if (!checkLoginStatus())
				return null;

			BuyRequest request = new BuyRequest(credits);
			out.writeObject(request);
			out.flush();

			Object response = null;
			try {
				response = in.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}

			if (response instanceof BuyResponse) {
				return (BuyResponse) response;
			}

			return null;
		}

		@Override
		@Command
		public Response list() throws IOException {
			if (!checkLoginStatus())
				return null;

			return null;
		}

		@Override
		@Command
		public Response download(String filename) throws IOException {
			if (!checkLoginStatus())
				return null;

			return null;
		}

		@Override
		@Command
		public MessageResponse upload(String filename) throws IOException {
			if (!checkLoginStatus())
				return null;

			return null;
		}

		@Override
		@Command
		public MessageResponse logout() throws IOException {
			if (!checkLoginStatus())
				return null;

			return null;
		}

		@Command
		public MessageResponse exit() throws IOException {
			shell.close();
			System.in.close();
			return null;
		}

		private boolean checkLoginStatus() throws IOException {
			if (loggedIn) return true;

			shell.writeLine("Please login first.");
			return false;
		}
	}
}