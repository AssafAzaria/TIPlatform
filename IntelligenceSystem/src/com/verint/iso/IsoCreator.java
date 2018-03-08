package com.verint.iso;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.verint.utils.ErrorLogger;

import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ConfigException;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ISO9660File;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ISO9660RootDirectory;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.CreateISO;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.ISO9660Config;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.ISOImageFileHandler;
import de.tu_darmstadt.informatik.rbg.hatlak.joliet.impl.JolietConfig;
import de.tu_darmstadt.informatik.rbg.mhartle.sabre.HandlerException;

/**
 * Creates an ISO image from a given file
 * 
 * NOTE: we use lib from local repository jiic 1.0.0. In the 1.2.2 pacakge that 
 * comes from maven repo, there is a silly bug with small named files (less than 
 * 8 chars).
 * 
 * @author Assaf Azaria
 */

public class IsoCreator {
	private static final String ISO_PATH = "isofiles";
	private static Logger logger = ErrorLogger.getInstance().getLogger();
	
	// Creates an iso that wraps the given file
	public static Path createIso(Path path) {
		// Output file
		Path dir = Paths.get(ISO_PATH);
		
		if (Files.notExists(dir)) {
			try {
				Files.createDirectories(dir);
			} catch (IOException e) {
				logger.log(Level.FINE, "failed to create iso dir", e);
			}
		}
		
		dir = dir.resolve(path.getFileName() + ".iso");
		
		File outfile = dir.toFile();
		logger.fine("Path to iso file: " + outfile.getAbsolutePath());
		
		try {
			// Directory hierarchy, starting from the root
			ISO9660RootDirectory.MOVED_DIRECTORIES_STORE_NAME = "rr_moved";
			ISO9660RootDirectory root = new ISO9660RootDirectory();
			root.addFile(new ISO9660File(path.toString()));

			// ISO9660 support
			ISO9660Config iso9660Config = createIsoConfig();
			JolietConfig jolietConfig = createJolietConfig();

			ISOImageFileHandler streamHandler = new ISOImageFileHandler(outfile);
			CreateISO iso = new CreateISO(streamHandler, root);
			iso.process(iso9660Config, null, jolietConfig, null);
			logger.fine("Iso created. ");
		} catch (HandlerException | FileNotFoundException e) {
			logger.severe("problem creating iso");
			logger.log(Level.FINE, "", e);
		}

		return outfile.toPath();
	}

	private static ISO9660Config createIsoConfig() {
		ISO9660Config iso9660Config = new ISO9660Config();
		try {
			iso9660Config.allowASCII(false);
			iso9660Config.setInterchangeLevel(1);
			iso9660Config.restrictDirDepthTo8(true);
			iso9660Config.setVolumeID("ISO Test Jiic");
			iso9660Config.forceDotDelimiter(true);
		} catch (ConfigException e) {
			logger.log(Level.FINE, "iso config problem", e);
		}

		return iso9660Config;
	}

	private static JolietConfig createJolietConfig() {
		JolietConfig jolietConfig = new JolietConfig();
		try {
			jolietConfig.setPublisher("Assaf_Azaria");
			jolietConfig.setVolumeID("EdrTest");
			jolietConfig.setDataPreparer("Assaf_Azaria");
			jolietConfig.forceDotDelimiter(true);
		} catch (ConfigException e) {
			logger.log(Level.FINE, "Joliet Config problem", e);
		}

		return jolietConfig;
	}
	
	public static void main(String[] args) throws Exception {
		Path f = Paths.get("C:\\Users\\Assaf\\Desktop\\Verint\\TestFiles\\Benign\\kitty\\kitty.exe");
		System.out.println(f.toFile().exists());
		System.out.println(f.getFileName());
		System.out.println(f.toString());

		Path p = createIso(f);
		System.out.println(p);
	}


}