// La classe BufferManager gère la RAM.

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class BufferManager {
    private DBConfig config;
    private DiskManager diskManager; // Gestionnaire disque
    private String algoChoix;
    private int numBuffers;
    private ByteBuffer[] ramAllouee;
    private PageId[] idPagesEnMemoire;
    private int[] pinCounts;
    private boolean[] flagsDirty;
    private long[] derniereUtilisation;
    private Map<PageId, Integer> repertoirePages;

    // Constructeur
    public BufferManager(DBConfig config, DiskManager diskManager) {
        this.config = config;
        this.diskManager = diskManager;
        this.algoChoix = config.getBm_policy();
        this.numBuffers = config.getBm_buffercount();

        // Préparation listes et cases vides
        this.ramAllouee = new ByteBuffer[numBuffers];
        this.idPagesEnMemoire = new PageId[numBuffers];
        this.pinCounts = new int[numBuffers];
        this.flagsDirty = new boolean[numBuffers];
        this.derniereUtilisation = new long[numBuffers];
        this.repertoirePages = new HashMap<>();

        // Création des cases vides en mémoire
        for (int i = 0; i < numBuffers; i++) {
            this.ramAllouee[i] = ByteBuffer.allocate(config.getPagesize());
            this.idPagesEnMemoire[i] = null;
            this.pinCounts[i] = 0;
            this.flagsDirty[i] = false;
            this.derniereUtilisation[i] = 0;
        }
    }
    
    // demande d'accès page de données
    public ByteBuffer GetPage(PageId pageId) throws IOException {
        
    	// On vérifie si la page est déjà chargée en mémoire
    	if (repertoirePages.containsKey(pageId)) {
            int numCase = repertoirePages.get(pageId);
            pinCounts[numCase]++; // signalement de l'utilisation de la page
            derniereUtilisation[numCase] = System.nanoTime(); //maj heure d'accès
            return ramAllouee[numCase];
        }

        int caseALiberer = ChooseVictimFrame();
        if (flagsDirty[caseALiberer]) {
            diskManager.WritePage(idPagesEnMemoire[caseALiberer], ramAllouee[caseALiberer]);
        }
        if (idPagesEnMemoire[caseALiberer] != null) {
            repertoirePages.remove(idPagesEnMemoire[caseALiberer]);
        }

        diskManager.ReadPage(pageId, ramAllouee[caseALiberer]);
        idPagesEnMemoire[caseALiberer] = pageId;
        pinCounts[caseALiberer] = 1;
        flagsDirty[caseALiberer] = false;
        derniereUtilisation[caseALiberer] = System.nanoTime();
        repertoirePages.put(pageId, caseALiberer);

        return ramAllouee[caseALiberer];
    }
    
    // Méthode permettant de signaler qu'une page a fini d'être utilisée.
    public void FreePage(PageId pageId, boolean valdirty) {
        if (!repertoirePages.containsKey(pageId)) return;
        
        int numCase = repertoirePages.get(pageId);
        
        // Décrémentation du compteur d'utilisateurs
        if (pinCounts[numCase] > 0) pinCounts[numCase]--;
        flagsDirty[numCase] = flagsDirty[numCase] || valdirty;
    }
    
    // Sauvegarde données modifiées et vide la mémoire
    public void FlushBuffers() throws IOException {
        for (int i = 0; i < numBuffers; i++) {
            // Si une page est modifiée, on l'écrit sur le disque dur
        	if (flagsDirty[i]) {
                diskManager.WritePage(idPagesEnMemoire[i], ramAllouee[i]);
                flagsDirty[i] = false;
            }
            idPagesEnMemoire[i] = null;
            pinCounts[i] = 0;
            flagsDirty[i] = false;
            derniereUtilisation[i] = 0;
            ramAllouee[i].clear();
        }
        repertoirePages.clear();
    }
    
    // Selection LRU/MRU
    public void SetCurrentReplacementPolicy(String policy) {
        if (!policy.equals("LRU") && !policy.equals("MRU")) throw new IllegalArgumentException("Politique inconnue");
        this.algoChoix = policy;
    }

    private int ChooseVictimFrame() {
        for (int i = 0; i < numBuffers; i++) if (idPagesEnMemoire[i] == null) return i;

        int caseALiberer = -1;
        long policyDate = (algoChoix.equals("LRU")) ? Long.MAX_VALUE : Long.MIN_VALUE;

        for (int i = 0; i < numBuffers; i++) {
            if (pinCounts[i] == 0) {
                if ((algoChoix.equals("LRU") && derniereUtilisation[i] < policyDate) ||
                    (algoChoix.equals("MRU") && derniereUtilisation[i] > policyDate)) {
                    policyDate = derniereUtilisation[i];
                    caseALiberer = i;
                }
            }
        }
        if (caseALiberer == -1) throw new IllegalStateException("Buffer pool plein.");
        return caseALiberer;
    }
    
    public int getPagePinCount(PageId pageId) {
        if (repertoirePages.containsKey(pageId)) return pinCounts[repertoirePages.get(pageId)];
        return 0;
    }
    
    public boolean isPageDirty(PageId pageId) {
        if (repertoirePages.containsKey(pageId)) return flagsDirty[repertoirePages.get(pageId)];
        return false;
    }
}