package server;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.TimerTask;

public class AliveTask extends TimerTask{
	private String proxyHost;
	private int proxyPort;
	private int port;

	public AliveTask(String proxyHost, int proxyPort, int port) {
		this.proxyHost = proxyHost;
		this.proxyPort = proxyPort;
		this.port = port;
	}

	public void run() {
		byte[] portEncoded = ByteBuffer.allocate(4).putInt(port).array();
		DatagramPacket packet = new DatagramPacket(portEncoded, 4);

		try {
			DatagramSocket udp;
			udp = new DatagramSocket();
			udp.connect(InetAddress.getByName(proxyHost), proxyPort);
			udp.send(packet);
		} catch (IOException e) {
			System.out.println("Sending alive package failed: " + e.getMessage());
			e.printStackTrace();
		}
	}
}