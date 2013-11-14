package proxy;

import ownModel.FileServerId;
import ownModel.FileServerInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class AliveReceivingTask extends TimerTask {
	private final DatagramSocket socket;
	private final int checkPeriod;
	private final HashMap<FileServerId, ownModel.FileServerInfo> fileServerList;

	public AliveReceivingTask(DatagramSocket socket, int checkPeriod, HashMap<FileServerId, ownModel.FileServerInfo> fileServerList) {
		this.socket = socket;
		this.checkPeriod = checkPeriod;
		this.fileServerList = fileServerList;
	}

	public static void init(DatagramSocket socket, int checkPeriod, HashMap<FileServerId, ownModel.FileServerInfo> fileServerList) {
		TimerTask task = new AliveReceivingTask(socket, checkPeriod, fileServerList);

		Timer timer = new Timer("AliveReceiver");
		timer.schedule(task, 0, checkPeriod);
	}

	@Override
	public void run() {
		try {
			byte[] buf = new byte[4];
			DatagramPacket data = new DatagramPacket(buf, 4);

			socket.setSoTimeout(checkPeriod);

			while (true) {
				socket.receive(data);
				handleMessage(data, buf);
			}
		} catch (SocketTimeoutException e) {
			// Socket time out
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleMessage(DatagramPacket packet, byte[] data) {
		int port = ByteBuffer.wrap(data).asIntBuffer().get();
		String host = packet.getAddress().getHostAddress();

		FileServerId id = new FileServerId(host, port);

		if (fileServerList.containsKey(id)) {
			fileServerList.get(id).bumpLastSeen();
		} else {
			FileServerInfo info = new FileServerInfo(id, true, 0);
			fileServerList.put(id, info);
		}
	}
}
