package com.verint.moloch;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.verint.utils.SystemCommandCaller;
import com.verint.utils.Utils;

// TODO: should this be an engine controller? check
public class MolochController {
	private static final String MOLOCH_DIR = "/data/moloch/bin/"; // TODO: Read
																	// this from
																	// config
	//private Logger logger = ErrorLogger.getInstance().getLogger();
	//private MolochDataFetcher client = new MolochDataFetcher();
	
	public MolochController() {
	}

	public PcapData submitPcapToMoloch(Path pcap) {
		// keep them somewhere TODO:
		PcapData data = new PcapData(pcap);
		data.setStartTime(Instant.now().minusSeconds(1)); //TODO: check times.

		// call moloch capture with the file. We assume that we run on the
		// same machine as moloch // TODO:
		List<String> args = Arrays.asList("-c", "./moloch-capture --copy --flush -r " + pcap.toAbsolutePath());
		SystemCommandCaller.invoke(MOLOCH_DIR, "bash", args);
		
		return data;
	}

	public static void main(String[] args) {
		//Logger logger = ErrorLogger.getInstance().getLogger();
		Utils.setLoggerLevels(true);
		
		// Load the file
		String filename = "mycap3.pcap";
		DataType request = DataType.SESSIONS;
		
		if (args != null && args.length > 0) {
			filename = args[0];
			if (args.length > 1)
				request = DataType.valueOf(args[1]);
		}
		Path pcap = Paths.get("test_pcaps/" + filename);

		System.out.println(pcap);
		System.out.println(pcap.toAbsolutePath());

		//System.out.println(Utils.identifyFileType(pcap.toString()));
		MolochController cont =  new MolochController();
		PcapData data = cont.submitPcapToMoloch(pcap);
		//PcapData data = new PcapData(pcap);
		// TODO: wait for moloch to finish? - it is allready the case
		data.setEndTime(Instant.now().plusSeconds(3));
		System.out.println(data);
		
		MolochDataFetcher client = new MolochDataFetcher();
		
		request.apply(client, data);
		//client.getSpiViewData(data.getStartTimeAsEpoch(), data.getEndTimeAsEpoch());

	}

}
