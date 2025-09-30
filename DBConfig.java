package Config_db;

public class DBConfig {
	private String dbpath;
	public DBConfig(String dbpath) {
		this.dbpath=dbpath;
	}
	public static DBConfig LoadDBConfig(String fichier_config) {
		DBConfig config = new DBConfig(fichier_config);
		return config;
		
	}
}
