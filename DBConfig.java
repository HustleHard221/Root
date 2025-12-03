import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class DBConfig {

    private String dbpath;
    private int pagesize;
    private int dm_maxfilecount;
    private int bm_buffercount;
    private String bm_policy;

    public DBConfig(String dbpath, int pagesize, int dm_maxfilecount, int bm_buffercount, String bm_policy) {
        this.dbpath = dbpath;
        this.pagesize = pagesize;
        this.dm_maxfilecount = dm_maxfilecount;
        this.bm_buffercount = bm_buffercount;
        this.bm_policy = bm_policy;
    }

    public String getDbpath() { return this.dbpath; }
    public int getPagesize() { return this.pagesize; }
    public int getDm_maxfilecount() { return this.dm_maxfilecount; }
    public int getBm_buffercount() { return this.bm_buffercount; }
    public String getBm_policy() { return this.bm_policy; }

    public static DBConfig LoadDBConfig(String configFilePath) throws IOException {
        String dbpathValeur = null;
        Integer pagesizeValeur = null;
        Integer dm_maxfilecountValeur = null;
        Integer bm_buffercountValeur = null;
        String bm_policyValeur = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(configFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parties = line.split("=", 2);
                if (parties.length == 2) {
                    String nomParametre = parties[0].trim();
                    String valeur = parties[1].trim();
                    try {
                        switch (nomParametre) {
                            case "dbpath": dbpathValeur = valeur; break;
                            case "pagesize": pagesizeValeur = Integer.parseInt(valeur); break;
                            case "dm_maxfilecount": dm_maxfilecountValeur = Integer.parseInt(valeur); break;
                            case "bm_buffercount": bm_buffercountValeur = Integer.parseInt(valeur); break;
                            case "bm_policy": bm_policyValeur = valeur; break;
                        }
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Format invalide pour '" + nomParametre + "'", e);
                    }
                }
            }
        }

        if (dbpathValeur == null || pagesizeValeur == null || dm_maxfilecountValeur == null || 
            bm_buffercountValeur == null || bm_policyValeur == null) {
            throw new IllegalArgumentException("Paramètre manquant dans le fichier de configuration.");
        }

        return new DBConfig(dbpathValeur, pagesizeValeur, dm_maxfilecountValeur, bm_buffercountValeur, bm_policyValeur);
    }
}