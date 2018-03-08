package com.verint.tests;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class WatchServiceTest {

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception{
		WatchService watcher = FileSystems.getDefault().newWatchService();
		Path dir = Paths.get("files_to_test");
		WatchKey key = dir.register(watcher, ENTRY_CREATE);
		for (;;) {

		    // wait for key to be signaled
		    //WatchKey key;
		    try {
		        key = watcher.take();
		    } catch (InterruptedException x) {
		        return;
		    }

		    for (WatchEvent<?> event: key.pollEvents()) {
		        WatchEvent.Kind<?> kind = event.kind();

		        // This key is registered only for ENTRY_CREATE events,
		        // but an OVERFLOW event can occur regardless if events
		        // are lost or discarded.
		        if (kind == OVERFLOW) {
		            continue;
		        }

		        // The filename is the context of the event.
		        WatchEvent<Path> ev = (WatchEvent<Path>)event;
		        Path filename = ev.context();
		     	
		        // Output
				System.out.println("New path created: " + filename);
		        System.out.println(Files.isDirectory(filename));
		        System.out.println(Files.isRegularFile(filename));
		        System.out.println(filename.toFile().isDirectory());
		        System.out.println(filename.toFile().list() != null);
		        
		        Path p2 = dir.resolve(filename);
		        System.out.println(p2);
		        System.out.println(Files.isDirectory(p2));
		        
		    }

		    // Reset the key -- this step is critical if you want to
		    // receive further watch events.  If the key is no longer valid,
		    // the directory is inaccessible so exit the loop.
		    boolean valid = key.reset();
		    if (!valid) {
		        break;
		    }
		}
	}
}
