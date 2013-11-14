package cli;

public class ShellThread implements Runnable {


	private Shell shell;

	public ShellThread(Shell shell) {
		this.shell = shell;
	}

	public static void initNewThread(Shell shell) {
		(new Thread(new ShellThread(shell))).start();
	}

	@Override
	public void run() {
		shell.run();
	}
}
