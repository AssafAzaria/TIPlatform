package com.verint.tests;
import java.io.File;

import de.tu_darmstadt.informatik.rbg.hatlak.eltorito.impl.ElToritoConfig;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ISO9660File;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.ISO9660RootDirectory;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.CreateISO;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.ISO9660Config;
import de.tu_darmstadt.informatik.rbg.hatlak.iso9660.impl.ISOImageFileHandler;
import de.tu_darmstadt.informatik.rbg.hatlak.joliet.impl.JolietConfig;
import de.tu_darmstadt.informatik.rbg.hatlak.rockridge.impl.RockRidgeConfig;

public class ISOtest1 {

    private static boolean enableJoliet    = true;
    private static boolean enableRockRidge = true;
    private static boolean enableElTorito  = true;

    private static void handleOption(String option) {
      if (option.equals("disable-joliet")) {
          enableJoliet = false;
      } else
      if (option.equals("disable-rockridge")) {
          enableRockRidge = false;
      } else
      if (option.equals("disable-eltorito")) {
          enableElTorito = false;
      }
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Start");

        // Output file
        File outfile = new File(args.length>0 ? args[0] : "ISOTest3.iso");

        // Directory hierarchy, starting from the root
        ISO9660RootDirectory.MOVED_DIRECTORIES_STORE_NAME = "rr_moved";
        ISO9660RootDirectory root = new ISO9660RootDirectory();

        if (args.length > 1) {

            System.out.println("    If");

            // Record specified files and directories

            for (int i=1; i<args.length; i++) {
                if (args[i].startsWith("--")) {
                    handleOption(args[i].substring(2, args[i].length()));
                } else {
                    // Add file or directory contents recursively
                    File file = new File(args[i]);
                    if (file.exists()) {
                        if (file.isDirectory()) {
                            root.addContentsRecursively(file);
                        } else {
                            root.addFile(file);
                        }
                    }
                }
            }
        } else {
            // Record test cases
            // Additional test cases
            // (file without extension, tar.gz, deeply nested directory;
            // sort order tests, renaming tests: filename + extension,
            // directory with many files: sector end test)
            root.addRecursively(new File("test"));

            ISO9660File file1 = new ISO9660File("test/tux.gif", 1);
            root.addFile(file1);
            ISO9660File file10 = new ISO9660File("test/tux.gif", 10);
            root.addFile(file10);
            ISO9660File file12 = new ISO9660File("test/tux.gif", 12);
            root.addFile(file12);
        }


        // ISO9660 support
        System.out.println("ISO9660 support");
        ISO9660Config iso9660Config = new ISO9660Config();
        iso9660Config.allowASCII(false);
        iso9660Config.setInterchangeLevel(1);
        iso9660Config.restrictDirDepthTo8(true);
        iso9660Config.setPublisher("Jens Hatlak");
        iso9660Config.setVolumeID("ISO Test");
        iso9660Config.setDataPreparer("Jens Hatlak");

        iso9660Config.setCopyrightFile(new File("Copyright.txt"));
        iso9660Config.forceDotDelimiter(true);

        RockRidgeConfig rrConfig = null;
        if (enableRockRidge) {
            // Rock Ridge support
            rrConfig = new RockRidgeConfig();
            rrConfig.setMkisofsCompatibility(false);
            rrConfig.hideMovedDirectoriesStore(true);
            rrConfig.forcePortableFilenameCharacterSet(true);
        }

        JolietConfig jolietConfig = null;
        if (enableJoliet) {
            // Joliet support
            jolietConfig = new JolietConfig();
            jolietConfig.setPublisher("Test 1");
            jolietConfig.setVolumeID("Joliet Test");
            jolietConfig.setDataPreparer("Jens Hatlak");
            jolietConfig.setCopyrightFile(new File("Copyright.txt"));
            jolietConfig.forceDotDelimiter(true);
        }

        ElToritoConfig elToritoConfig = null;
        if(enableElTorito) {

            elToritoConfig = new ElToritoConfig(new File("tomsrtbt-2.0.103.ElTorito.288.img"),
                                                         ElToritoConfig.BOOT_MEDIA_TYPE_2_88MEG_DISKETTE,
                                                         ElToritoConfig.PLATFORM_ID_X86, "isoTest", 4,
                                                         ElToritoConfig.LOAD_SEGMENT_7C0);
        }

        // Create ISO
        System.out.println("Create ISO");
        ISOImageFileHandler streamHandler = new ISOImageFileHandler(outfile);
        System.out.println("streamHandler");
        CreateISO iso = new CreateISO(streamHandler, root);
        System.out.println("iso");
        iso.process(iso9660Config, rrConfig, jolietConfig, elToritoConfig);
        System.out.println("process");
        System.out.println("Done. File is: " + outfile);
    }
}