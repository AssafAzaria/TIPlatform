package com.verint.tests;

import java.util.function.Predicate;

import org.apache.tika.mime.MediaType;

import com.verint.utils.Utils;

public class TestMime
{

	public static void main(String[] args)
	{
//		Predicate<MediaType> isSupported = 
//				(type) -> type.equals(Utils.PCAP_MIME);

		Predicate<MediaType> isSupported = 
				(type) -> (type.getType().equals("application") &&
						   !type.equals(Utils.PCAP_MIME));
				
		System.out.println(isSupported.test(Utils.PCAP_MIME));
	}

}
