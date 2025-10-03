public class DBConfig {
	private String dbpath;
	private byte pagesize;
	private int dm_maxfilecount;
	public DBConfig(String dbpath) {
		this.dbpath=dbpath;
		this.pagesize=pagesize;
		this.dm_maxfilecount=dm_maxfilecount;
	}
	public static DBConfig LoadDBConfig(String fichier_config) {
		DBConfig config = new DBConfig(fichier_config);
		return config;
		
	}
}
