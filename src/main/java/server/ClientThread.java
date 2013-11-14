package server;

import message.Request;
import message.Response;
import message.request.*;
import message.response.ListResponse;
import message.response.MessageResponse;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;

public class ClientThread implements Runnable, IFileServer {

	private final ServerSocket socket;
	private final String dir;

	public ClientThread(ServerSocket socket, String dir) {
		this.socket = socket;
		this.dir = dir;
	}

	public static void initNewThread(ServerSocket socket, String dir) {
		(new Thread(new ClientThread(socket, dir))).start();
	}

	@Override
	public void run() {
		Socket connection;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try {
			connection = socket.accept();
			initNewThread(socket, dir);

			out = new ObjectOutputStream(connection.getOutputStream());
//			streamList.add(out);
			out.flush();
			in = new ObjectInputStream(connection.getInputStream());

			while (true) {
				Object request = in.readObject();
				Response response;

				if (request instanceof ListRequest) {
					response = list();
				} else if (request instanceof UploadRequest) {
					response = upload((UploadRequest) request);
				} else if (request instanceof Request) {
					response = new MessageResponse("Unsupported Request: " + request.getClass());
				} else {
					response = new MessageResponse("Got a non-request object");
				}

				out.writeObject(response);
				out.flush();
			}
		} catch (EOFException ignored) {
			// Client aborted connection
		} catch (SocketException ignored) {
			// Server is shutting down
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException ignored) {}
			}
		}
	}

	@Override
	public Response list() throws IOException {
		File uploadDir = new File(dir);
		HashSet<String> files = new HashSet<String>();
		for (File file: uploadDir.listFiles()) {
			files.add(file.getName());
		}
		return new ListResponse(new HashSet<String>(files));
	}

	@Override
	public Response download(DownloadFileRequest request) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	/**
	 * @todo
	 */
	@Override
	public Response info(InfoRequest request) throws IOException {
		return new MessageResponse("Method info not implemented");
	}

	/**
	 * @todo
	 */
	@Override
	public Response version(VersionRequest request) throws IOException {
		return new MessageResponse("Method version not implemented");
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(dir + "/" + request.getFilename()));
			writer.write(new String(request.getContent()));
		} finally {
			if (writer != null) writer.close();
		}

		System.out.println(dir + "/" + request.getFilename());

		return new MessageResponse("File written.");
	}

}
