package proxy;

import message.Request;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.LoginRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.LoginResponse;
import util.Config;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class ProxyImpl {

	static final HashMap<String, Integer> creditList   = new HashMap<String, Integer>();
	static HashMap<String, String>  passwordList = new HashMap<String, String>();

	public static void main(String[] args) throws IOException {
		Config config = new Config("proxy");

		int portTCP, portUDP, timeout, checkPeriod;

		try {
			portTCP = config.getInt("tcp.port");
			portUDP = config.getInt("udp.port");
			timeout = config.getInt("fileserver.timeout");
			checkPeriod = config.getInt("fileserver.checkPeriod");
		} catch (MissingResourceException e) {
			System.out.println("A configuration parameter is missing: " + e.getMessage());
			System.exit(1);
			return;
		}

		Config userConfig = new Config("user");
		Enumeration<String> users = ResourceBundle.getBundle("user").getKeys();

		while (users.hasMoreElements()) {
			String configLine = users.nextElement();
			String user = configLine.substring(0, configLine.indexOf('.'));
			String key = configLine.substring(configLine.indexOf('.') + 1);

			if (key.equals("credits") && !creditList.containsKey(user)) {
				creditList.put(user, userConfig.getInt(configLine));

			} else if (key.equals("password") && !passwordList.containsKey(user)) {
				passwordList.put(user, userConfig.getString(configLine));
			}
		}

		ServerSocket tcpSocket = new ServerSocket(portTCP);
		TCPThread.initNewThread(tcpSocket);
	}

	private static class TCPThread implements Runnable {

		private ServerSocket tcpSocket;

		private TCPThread(ServerSocket tcpSocket) {
			this.tcpSocket = tcpSocket;
		}

		static public void initNewThread(ServerSocket tcpSocket) {
			(new Thread(new TCPThread(tcpSocket))).start();
		}

		public void run() {
			Socket socket;
			try {
				socket = tcpSocket.accept();
				initNewThread(tcpSocket);

				ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
				out.flush();
				ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

				Object request;
				String user = "";

				while (true) {
					request = in.readObject();

					if (!(request instanceof Request)) {
						System.out.println("Invalid data");
						throw new IOException();
					}

					if (request instanceof LoginRequest) {
						LoginRequest loginRequest = (LoginRequest) request;
						LoginResponse response;
						if (! passwordList.containsKey(loginRequest.getUsername())) {
							// Check if user is known - The type enum does not support this so just write WRONG_CREDENTIALS
							response = new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
						} else if (! passwordList.get(loginRequest.getUsername()).equals(loginRequest.getPassword())) {
							// Check if passwort is correct
							response = new LoginResponse(LoginResponse.Type.WRONG_CREDENTIALS);
						} else {
							user = loginRequest.getUsername();
							response = new LoginResponse(LoginResponse.Type.SUCCESS);
						}
						out.writeObject(response);
						out.flush();
					} else if (request instanceof CreditsRequest) {
						int credits;
						synchronized (creditList) {
							credits = creditList.get(user);
						}
						CreditsResponse response = new CreditsResponse(credits);
						out.writeObject(response);
						out.flush();
					} else if (request instanceof BuyRequest) {
						int credits;
						long creditsToBeBought = Math.max(0, ((BuyRequest) request).getCredits());
						synchronized (creditList) {
							credits = creditList.get(user);
							credits += creditsToBeBought;
							creditList.put(user, credits);
						}
						BuyResponse response = new BuyResponse(credits);
						out.writeObject(response);
						out.flush();
					}
				}
			} catch (EOFException unused) {
				// Client aborted connection
				return;
			} catch (IOException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}
	}

//	private static class UDPThread implements Runnable {
//
//		private DatagramSocket udpSocket;
//
//		private UDPThread(DatagramSocket udpSocket) {
//			this.udpSocket = udpSocket;
//		}
//
//		static public void initNewThread(DatagramSocket udpSocket) {
//			(new Thread(new UDPThread(udpSocket))).run();
//		}
//
//		public void run() {
//			Socket socket = null;
//			try {
//				socket = udpSocket.receive();
//
//				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
//				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//
//				System.out.println(in.readLine());
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//	}
}
