package server;

import message.Request;
import message.Response;
import message.request.*;
import message.response.DownloadFileResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.MessageResponse;
import model.DownloadTicket;
import util.ChecksumUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Scanner;

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
		Socket connection = null;
		ObjectOutputStream out = null;
		ObjectInputStream in = null;
		try {
			connection = socket.accept();
			initNewThread(socket, dir);

			out = new ObjectOutputStream(connection.getOutputStream());
			out.flush();
			in = new ObjectInputStream(connection.getInputStream());

			while (true) {
				requestHandlingLoop(out, in);
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
			if (connection != null) {
				try {
					connection.close();
				} catch (IOException ignored) {}
			}
		}
	}

	private void requestHandlingLoop(ObjectOutputStream out, ObjectInputStream in) throws IOException, ClassNotFoundException {
		Object request = in.readObject();
		Response response;

		if (request instanceof ListRequest) {
			response = list();
		} else if (request instanceof UploadRequest) {
			response = upload((UploadRequest) request);
		} else if (request instanceof InfoRequest) {
			response = info((InfoRequest) request);
		} else if (request instanceof DownloadFileRequest) {
			response = download((DownloadFileRequest) request);
		} else if (request instanceof Request) {
			response = new MessageResponse("Unsupported Request: " + request.getClass());
		} else {
			response = new MessageResponse("Got a non-request object");
		}

		out.writeObject(response);
		out.flush();
	}

	@Override
	public Response list() throws IOException {
		File uploadDir = new File(dir);
		if (!uploadDir.exists() || !uploadDir.isDirectory()) {
			return new MessageResponse("FATAL: Upload directory does not exist");
		}

		HashSet<String> files = new HashSet<String>();
		for (File file: uploadDir.listFiles()) {
			files.add(file.getName());
		}
		return new ListResponse(new HashSet<String>(files));
	}

	@Override
	public Response download(DownloadFileRequest request) throws IOException {
		DownloadTicket ticket = request.getTicket();
		File file = new File(dir + "/" + ticket.getFilename());
		if (!file.exists() || !file.isFile()) {
			return new MessageResponse("Requested file " + ticket.getFilename() + " does not exist");
		}

		boolean result = ChecksumUtils.verifyChecksum(ticket.getUsername(), file, 1, ticket.getChecksum());
		if (!result) {
			return new MessageResponse("Checksum Error");
		}

		String text = new Scanner(file).useDelimiter("\\A").next();
		return new DownloadFileResponse(ticket, text.getBytes());
	}

	@Override
	public Response info(InfoRequest request) throws IOException {
		File file = new File(dir + "/" + request.getFilename());
		if (!file.exists() || !file.isFile()) {
			return new MessageResponse("Requested file " + request.getFilename() + " does not exist");
		}

		return new InfoResponse(request.getFilename(), file.length());
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

		return new MessageResponse("File written.");
	}

}
