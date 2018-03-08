package com.verint.moloch;

public enum DataType {
	CONNECTIONS("connections"){
		@Override
		void apply(MolochDataFetcher fetcher, PcapData data) {
			fetcher.getConnectionsData(data.getStartTimeAsEpoch(), data.getEndTimeAsEpoch());
		}
	},
	SESSIONS("sessions"){
		@Override
		void apply(MolochDataFetcher fetcher, PcapData data) {
			fetcher.getSessionsData(data.getStartTimeAsEpoch(), data.getEndTimeAsEpoch());
		}
	},
	SPI_GRAPH("spigraph"){
		@Override
		void apply(MolochDataFetcher fetcher, PcapData data) {
			fetcher.getSpiGraphData(data.getStartTimeAsEpoch(), data.getEndTimeAsEpoch());
		}
	},
	SPI_VIEW("spiview"){
		@Override
		void apply(MolochDataFetcher fetcher, PcapData data) {
			fetcher.getSpiViewData(data.getStartTimeAsEpoch(), data.getEndTimeAsEpoch());
		}
	};
	
	
	private String name;
	DataType(String name)
	{
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	abstract void apply(MolochDataFetcher fetcher, PcapData data);
}
