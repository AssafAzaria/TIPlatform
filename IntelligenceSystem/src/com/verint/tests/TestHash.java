package com.verint.tests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.bind.DatatypeConverter;

import org.apache.tika.mime.MediaType;

import com.verint.utils.Utils;

public class TestHash {

	public static void main(String[] args) throws Exception{
		
//		String benign_dir = "C:\\Users\\Assaf\\Desktop\\Verint\\TestFiles\\Benign\\";
//		String path = benign_dir + "\\kitty\\kitty.exe";
//		
//		System.out.println("SHA-256 ***************************************");
//		showFileHash(Paths.get(path), "SHA-256");
//		
//		System.out.println("MD5 *******************************************");
//		showFileHash(Paths.get(path), "MD5");
		
		Path dir = 
				Paths.get("test/");
		
		// get all files
		// Load files
		List<Path> filesInFolder = Files.walk(dir)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
		System.out.println("Loaded " + filesInFolder.size() + " files");
		
		System.out.println("--------- only name -----------------");
		filesInFolder.forEach(e-> System.out.println(e + " : " + Utils.identifyFileTypeUsingNameOnly(e.toString())));
		
		System.out.println("-------- with file ----------");
		
		filesInFolder.forEach(e-> {
			try {
				String type = Utils.identifyFileType(e);
				System.out.println(e + " : " + type);
				MediaType mType = MediaType.parse(type);
				System.out.println("Mime: " + mType);
				
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		});
		// application/x-dosexec
		// application/pdf
		// application/x-ms-installer
		// application/zip
		
//		System.out.println("--------Media Type --------");
//		
//		filesInFolder.forEach(e-> {
//			System.out.println(e + " : " + MimetypesFileTypeMap
//				    .getDefaultFileTypeMap()
//				    .getContentType(e.toString()));	
//		});
		
		
		
		
		//System.out.println(MimeTypes.getDefaultMimeTypes().);
	}
	
	
	
	public static void showFileHash(Path file, String algorithm) throws IOException, NoSuchAlgorithmException
	{
		MessageDigest md = MessageDigest.getInstance(algorithm);
		md.update(Files.readAllBytes(file));
		byte[] digest = md.digest();
		
		
		System.out.println(Arrays.toString(digest));
		
		String digestInHex = DatatypeConverter.printHexBinary(digest).toUpperCase();
		System.out.println(digestInHex);
		System.out.println();
	}

}
