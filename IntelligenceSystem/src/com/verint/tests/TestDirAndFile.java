package com.verint.tests;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

public class TestDirAndFile {

	public static void main(String[] args) throws IOException {
		Path path = Paths.get("files_to_test/progra-32");
		System.out.println(path);
		System.out.println(Files.isDirectory(path));
		System.out.println(Files.isRegularFile(path));

		Files.walk(path).filter(
				Files::isRegularFile).
				forEach(f -> addToQueue(f));

	}

	public static void addToQueue(Path f) {
		System.out.println("adding " + f);
	}

	private void waitForFileToFinishCopying(Path file) throws IOException {

		// Tried: Getting last modified time. Renaming. Getting write access.
		// nothing works. 
		try {
			TimeUnit.SECONDS.sleep(13);
		} catch (InterruptedException e2) {
			// logger.finer("interrupt, nevermind");
		}

		// Path renamed = file.resolveSibling(file.getFileName() + "_tmp");
		// System.out.println("Renamed " + renamed);
		//
		// for (;;) {
		// try {
		// Path success = Files.move(file, renamed);
		// logger.fine("success " + success);
		// Path success2 = Files.move(success, file);
		// logger.fine("reverted. event? " + success2);
		//
		// try {
		// Thread.sleep(5000);
		// } catch (InterruptedException e1) {
		// e1.printStackTrace();
		// }
		// return;
		// } catch (Exception e) {
		// logger.fine(e.getClass().getSimpleName() + " " + e.getMessage());
		// try {
		// Thread.sleep(1500);
		// } catch (InterruptedException e1) {
		// e1.printStackTrace();
		// }
		// }
		// }

		// boolean isGrowing = false;

		// FileTime lastModified = Files.getLastModifiedTime(file);
		// long initialWeight = 0;
		// long finalWeight = 0;
		//
		// do {
		// initialWeight = Files.getLastModifiedTime(file).toMillis();
		// try {
		// System.out.println("wait a 1 sec");
		// Thread.sleep(10000);
		// } catch (InterruptedException e) {
		// 
		// e.printStackTrace();
		// }
		// finalWeight = Files.getLastModifiedTime(file).toMillis();
		// System.out.println("init: " + initialWeight);
		// System.out.println("final : " + finalWeight);
		//
		// isGrowing = initialWeight < finalWeight;
		// } while (isGrowing);
		//
		// System.out.println("Finished creating file!");

		//boolean locked = true;
		//
		// while (locked) {
		// RandomAccessFile raf = null;
		// try {
		// raf = new RandomAccessFile(file.toFile(), "r"); // it will throw
		// // FileNotFoundException.
		// // It's not needed to
		// // use 'rw' because if
		// // the file is delete
		// // while copying, 'w'
		// // option will create an
		// // empty file.
		// raf.seek(file.toFile().length()); // just to make sure
		// // everything was
		// // copied, goes to the last byte
		// locked = false;
		// } catch (IOException e) {
		// locked = file.toFile().exists();
		// if (locked) {
		// System.out.println("File locked: '" + file.toFile().getAbsolutePath()
		// + "'");
		// try {
		// Thread.sleep(1000);
		// } catch (InterruptedException e1) {
		// e1.printStackTrace();
		// } // waits some time
		// } else {
		// System.out.println("File was deleted while copying: '" +
		// file.toFile().getAbsolutePath() + "'");
		// }
		// } finally {
		// if (raf != null) {
		// try {
		// raf.close();
		// } catch (Exception e) {
		// }
		// }
		// }
		// }
	}

	// Doesn't work. cannot lock on dir
	private void waitForFileToFinishCopying1(Path file) {
		while (true) {
			try {

				// Get a file channel for the file
				System.out.println("Asking for channel");

				FileChannel channel = FileChannel.open(file, StandardOpenOption.APPEND);

				System.out.println("Channel opened. is this enough?");

				// Use the file channel to create a lock on the file.
				// This method blocks until it can retrieve the lock.
				FileLock lock = channel.lock();

				System.out.println("Got the lock! copy should be finished");

				// Release the lock - if it is not null!
				if (lock != null) {
					lock.release();
				}

				// Close the file
				channel.close();
				break;
			} catch (Exception e) {

				System.out.println(e.getClass().getSimpleName() + " : " + e.getMessage());
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}

			}
		}
	}

}
