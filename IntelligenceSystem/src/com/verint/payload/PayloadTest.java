package com.verint.payload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.verint.payload.data.PayloadPcapResponse;
import com.verint.payload.data.PayloadResponse;

public class PayloadTest
{

	static PayloadApi payload = new PayloadApi();

	public static void main(String[] args) throws Exception
	{
		// String name =
		// "42fbcf4fd85bb267ee28e18e162073d4ab0fa3624e2d65d6ebfe050ebcee7093";
		String name = "6518e1e6cae4617ace4c480bf94036a05360f218a4e52652fe635673ea21085f";
		String folder = "vt_downloads \\20171018112950\\";

		// scanFile(Paths.get(folder + name));
		getJson(name);

		getPcap(name);

	}

	public static void scanFile(Path file)
	{
		PayloadResponse res = payload.scanFile(file.toFile());
		System.out.println(res);
	}

	public static void getJson(String hash)
	{
		PayloadResponse json = payload.getJsonReport(hash);
		if (json != null) {
			System.out.println(json.getResponseCode());
			System.out.println(json.getError());

		}
	}

	public static void getPcap(String hash)
	{
		PayloadPcapResponse pcap = payload.getPcapReport(hash);
		if (pcap != null) {
			System.out.println(pcap.toString());

			// copy pcap to file
			try {
				Files.write(Paths.get("pcaps/" + hash + ".pcap"), pcap.getCompressedPcap());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
