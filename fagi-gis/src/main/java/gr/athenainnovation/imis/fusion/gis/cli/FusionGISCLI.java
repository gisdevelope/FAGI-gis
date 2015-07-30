
package gr.athenainnovation.imis.fusion.gis.cli;

import com.hp.hpl.jena.graph.BulkUpdateHandler;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.algebra.Op;
import com.hp.hpl.jena.sparql.algebra.OpAsQuery;
import com.hp.hpl.jena.sparql.algebra.op.OpProject;
import com.hp.hpl.jena.sparql.core.BasicPattern;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import gr.athenainnovation.imis.fusion.gis.clustering.GeoClusterer;
import gr.athenainnovation.imis.fusion.gis.core.GeometryFuser;
import gr.athenainnovation.imis.fusion.gis.core.Link;
import gr.athenainnovation.imis.fusion.gis.gui.listeners.ErrorListener;
import gr.athenainnovation.imis.fusion.gis.gui.workers.Dataset;
import gr.athenainnovation.imis.fusion.gis.gui.workers.FuseWorker;
import gr.athenainnovation.imis.fusion.gis.gui.workers.FusionState;
import static gr.athenainnovation.imis.fusion.gis.gui.workers.FusionState.ANSI_RED;
import static gr.athenainnovation.imis.fusion.gis.gui.workers.FusionState.ANSI_RESET;
import static gr.athenainnovation.imis.fusion.gis.gui.workers.FusionState.ANSI_YELLOW;
import gr.athenainnovation.imis.fusion.gis.gui.workers.ImporterWorker;
import gr.athenainnovation.imis.fusion.gis.postgis.DatabaseInitialiser;
import gr.athenainnovation.imis.fusion.gis.postgis.PostGISImporter;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.Synset;
import net.didion.jwnl.data.relationship.AsymmetricRelationship;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.data.relationship.RelationshipList;
import net.didion.jwnl.dictionary.Dictionary;
import net.didion.jwnl.dictionary.MorphologicalProcessor;
import virtuoso.jena.driver.VirtGraph;
//import org.apache.commons.lang3.StringUtils;

/**
 * Entry of the program
 * 
 * @author nicks
 */

public class FusionGISCLI {
    private static final String SAME_AS = "http://www.w3.org/2002/07/owl#sameAs";
    private static final String PATH_TO_WORDNET = "/usr/share/wordnet";
    private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(FusionGISCLI.class);        
    private class FAGILogger implements ErrorListener {
        @Override
        public void notifyError(String message) {
            System.out.println("ERROR:"+message);
        }
    
    }
    
    private static FAGILogger errListen;
        
    public static void main(String args[]) {      
        /* Clustering test */
        
        
        List<String> lines;
        long startTime, endTime;
        String config_file;
        /*
        HashSet<String> stopWordsList = new HashSet<>();
        FileInputStream fstream = null;
        try {
            fstream = new FileInputStream("/home/nick/NetBeansProjects/test/test/stop-words_english_2_en.txt");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        }
    try (BufferedReader br = new BufferedReader(new InputStreamReader(fstream))) {
        String strLine;

        //Read File Line By Line
        while ((strLine = br.readLine()) != null)   {
            // Print the content on the console
            System.out.println (strLine);
            stopWordsList.add(strLine);

        }

        System.out.println(stopWordsList.size());

        //Close the input stream
    } catch (IOException ex) {
        //Logger.getLogger(WordsSerialize.class.getName()).log(Level.SEVERE, null, ex);
    }
    
     try (
      OutputStream file = new FileOutputStream("/home/nick/NetBeansProjects/test/test/FAGI-gis-CLI/src/main/resources/stopWordsUpdated.ser");
      OutputStream buffer = new BufferedOutputStream(file);
      ObjectOutput output = new ObjectOutputStream(buffer);
    ){
      output.writeObject(stopWordsList);
    }  
    catch(IOException ex){
        System.out.println("Exception");
        }*/
               
        if (args.length != 2) {
            System.out.println(args.length);
            //for(String a : args)
            System.out.println(ANSI_YELLOW+"Usage: FAGI -c configFile"+ANSI_RESET);
            return;
        }
        if (args[0].equals("-c")) {
            config_file = args[1];
        } else {
            System.out.println(ANSI_YELLOW+"Usage: FAGI -c configFile"+ANSI_RESET);
            return;
        }
        
        try {
            FusionState st = new FusionState();
            
            //lines = Files.readAllLines(Paths.get("/home/nick/Projects/FAGI-gis-master/fusion.conf"), Charset.defaultCharset());
            lines = Files.readAllLines(Paths.get(config_file), Charset.defaultCharset());
            for (String line : lines) {
                if (line.startsWith("#")) {
                } else if (line.equals("")) {
                } else {
                    String [] params = line.split("=");
                    st.setFusionParam(params[0].trim(), params[1].trim());
                }
            }
            
            boolean isValid = st.checkConfiguration();
            if ( isValid ) {
                System.out.println("-- Executing following Configuration");
                LOG.info(st);
            } else {
                return;
            }
            
            //GeoClusterer gc = new GeoClusterer(st.getDbConf());

            //gc.cluster(null);

            //return;
        
            String ret = createBulkLoadDir(st.getVirtDir());
            System.out.println("Ret "+ret);
            st.setVirtDir(ret);
            st.getDbConf().setBulkDir(ret);
            System.out.println("Ret "+st.getVirtDir());
            ArrayList<Link> links = (ArrayList<Link>) GeometryFuser.parseLinksFile(st.getLinksFile()); 
            createLinksGraph(links, st);
            
            if (st.isImported()) {
                final DatabaseInitialiser databaseInitialiser = new DatabaseInitialiser();
                databaseInitialiser.initialise(st.getDbConf());
            
                //final ImporterWorker datasetAImportWorker = new ImporterWorker(dbConfig, PostGISImporter.DATASET_A, sourceDatasetA, datasetAStatusField, errorListener);
                Dataset sourceADataset = new Dataset(st.getGraphConf().getEndpointA(), st.getGraphConf().getGraphA(), "");
                final ImporterWorker datasetAImportWorker = new ImporterWorker(st.getDbConf(), 
                        PostGISImporter.DATASET_A, sourceADataset, null, errListen);
                datasetAImportWorker.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override public void propertyChange(PropertyChangeEvent evt) {
                        if("progress".equals(evt.getPropertyName())) {
                            //System.out.println("prog");
                        }
                    }
                });
            
                Dataset sourceBDataset = new Dataset(st.getGraphConf().getEndpointB(), st.getGraphConf().getGraphB(), "");
                final ImporterWorker datasetBImportWorker = new ImporterWorker(st.getDbConf(), 
                        PostGISImporter.DATASET_B, sourceBDataset, null, errListen);
            
                datasetBImportWorker.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override public void propertyChange(PropertyChangeEvent evt) {
                        if("progress".equals(evt.getPropertyName())) {
                            //System.out.println("prog2");
                        }
                    }
                });
            
                startTime = System.nanoTime();
                datasetAImportWorker.execute();
                datasetBImportWorker.execute();
            
                datasetAImportWorker.get();
                datasetBImportWorker.get();
                endTime = System.nanoTime();
            }
            
            //System.out.println("Time spent importing data to PostGIS "+(endTime-startTime)/1000000000f);
            
            //final ScoreWorker scoreWorker = new ScoreWorker(st.getTransformation(), links, st.getDbConf(), st.getThreshold());
            
            //scoreWorker.execute();
            //scoresForAllRules.put(st.getTransformation().getID(), scoreWorker.get());
            
            boolean createNew = !st.getDstGraph().equals(st.getGraphConf().getGraphA());
            final FuseWorker fuseWorker = new FuseWorker(st.getTransformation(), links, st.getDbConf(),
                    st.getDstGraph(), createNew, st.getGraphConf(), null, null, errListen);

            fuseWorker.execute();
            fuseWorker.get();
        } catch (IOException ex) {
            if(ex instanceof NoSuchFileException) {
                System.out.println(ANSI_RED+args[1]+" does not exist"+ANSI_RESET);
                return;
            }
            Logger.getLogger(FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            //Logger.getLogger(FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
            SQLException exception = ex;
            while(exception != null) {
                Logger.getLogger(FusionGISCLI.class.getName()).log(Level.SEVERE, null, exception);
                exception = exception.getNextException();
            }
        } catch (InterruptedException ex) {
            Logger.getLogger("Int "+FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            Logger.getLogger("Exec "+FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger("Parse "+FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Done.");
    }
    
    private static VirtGraph getVirtuosoSet (String url, String username, String password) throws SQLException {
        //Class.forName("virtuoso.jdbc4.Driver");
        VirtGraph vSet = new VirtGraph ("jdbc:virtuoso://" + url + "/CHARSET=UTF-8", username, password);
        LOG.info(ANSI_YELLOW+"Virtuoso connection established."+ANSI_RESET);
        return vSet;
    }
    
    private static String createBulkLoadDir(String dir) throws IOException {
        System.out.println("Seps "+dir + " " + File.separator +" "+File.separatorChar);
        String ret = dir;
        int lastSlash = dir.lastIndexOf(File.separator);
        if (lastSlash != (dir.length() - 1 ) ) {
            System.out.println("Isxuei");
            ret = dir.concat(File.separator);
        }
        System.out.println("Seps "+ret + " " + File.separator +" "+File.separatorChar);
        File file = new File(ret);
        if (!file.exists()) {
            System.out.println("creating directory: " + ret);
            boolean result = false;

            try{
                file.mkdir();
                result = true;
            } catch(SecurityException se){
                //handle it
            }        
            if(result) {    
                System.out.println("DIR created");  
            }
        }
        
        file = new File(ret+"bulk_inserts/");
        if (!file.exists()) {
            System.out.println("creating directory: " + ret);
            boolean result = false;

            try{
                file.mkdir();
                result = true;
            } catch(SecurityException se){
                //handle it
            }        
            if(result) {    
                System.out.println("DIR created");  
            }
        }
        return ret;
    }
    
    private static void createLinksGraph(List<Link> lst, FusionState st) {
        try {
            final String dropGraph = "sparql DROP SILENT GRAPH <http://localhost:8890/DAV/all_links_"+st.getDbConf().getDBName()+">";
            final String createGraph = "sparql CREATE GRAPH <http://localhost:8890/DAV/all_links_"+st.getDbConf().getDBName()+">";
            VirtGraph set = getVirtuosoSet(st.getDbConf().getDBURL(), st.getDbConf().getUsername(), st.getDbConf().getPassword());
            //long endTime = System.nanoTime();
            //System.out.println("Time to connect : "+(endTime-startTime)/1000000000f);
            Connection virt_conn = set.getConnection();      
            PreparedStatement dropStmt;
            long starttime, endtime;
            dropStmt = virt_conn.prepareStatement(dropGraph);
            dropStmt.execute();
            PreparedStatement createStmt;
            createStmt = virt_conn.prepareStatement(createGraph);
            createStmt.execute();
            starttime = System.nanoTime();
            File f = new File(st.getDbConf().getBulkDir()+"bulk_inserts/selected_links.nt");
            //f.mkdirs();
            //f.getParentFile().mkdirs();
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
            final String bulk_insert = "DB.DBA.TTLP_MT (file_to_string_output ('"+st.getDbConf().getBulkDir()+"bulk_inserts/selected_links.nt'), '', "
                    +"'"+"http://localhost:8890/DAV/all_links_"+st.getDbConf().getDBName()+"')";
            //int stop = 0;
            if ( lst.size() > 0 ) {
                
                for(Link link : lst) {
                    //if (stop++ > 1000) break;
                    String triple = "<"+link.getNodeA()+"> <"+SAME_AS+"> <"+link.getNodeB()+"> .";
                    
                    out.println(triple);
                }
                out.close();
                
                PreparedStatement uploadBulkFileStmt;
                uploadBulkFileStmt = virt_conn.prepareStatement(bulk_insert);
                uploadBulkFileStmt.executeUpdate();
            }   endtime =  System.nanoTime();
            LOG.info(ANSI_YELLOW+"Linkares Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
            starttime = System.nanoTime();
            virt_conn.commit();
            //endtime =  System.nanoTime();
            LOG.info(ANSI_YELLOW+"Links Graph created in "+((endtime-starttime)/1000000000f)+""+ANSI_RESET);
        } catch (SQLException ex) {
            Logger.getLogger(FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(FusionGISCLI.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
        private static String getJWNL(String pathToWordnet){
        
        String jwnlXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<jwnl_properties language=\"en\">\n" +
        "	<version publisher=\"Princeton\" number=\"3.0\" language=\"en\"/>\n" +
        "	<dictionary class=\"net.didion.jwnl.dictionary.FileBackedDictionary\">\n" +
        "		<param name=\"morphological_processor\" value=\"net.didion.jwnl.dictionary.morph.DefaultMorphologicalProcessor\">\n" +
        "			<param name=\"operations\">\n" +
        "				<param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n" +
        "				<param value=\"net.didion.jwnl.dictionary.morph.DetachSuffixesOperation\">\n" +
        "					<param name=\"noun\" value=\"|s=|ses=s|xes=x|zes=z|ches=ch|shes=sh|men=man|ies=y|\"/>\n" +
        "					<param name=\"verb\" value=\"|s=|ies=y|es=e|es=|ed=e|ed=|ing=e|ing=|\"/>\n" +
        "					<param name=\"adjective\" value=\"|er=|est=|er=e|est=e|\"/>\n" +
        "                    <param name=\"operations\">\n" +
        "                        <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>\n" +
        "                        <param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n" +
        "                    </param>\n" +
        "				</param>\n" +
        "				<param value=\"net.didion.jwnl.dictionary.morph.TokenizerOperation\">\n" +
        "					<param name=\"delimiters\">\n" +
        "						<param value=\" \"/>\n" +
        "						<param value=\"-\"/>\n" +
        "					</param>\n" +
        "					<param name=\"token_operations\">\n" +
        "                        <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>\n" +
        "						<param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n" +
        "						<param value=\"net.didion.jwnl.dictionary.morph.DetachSuffixesOperation\">\n" +
        "							<param name=\"noun\" value=\"|s=|ses=s|xes=x|zes=z|ches=ch|shes=sh|men=man|ies=y|\"/>\n" +
        "							<param name=\"verb\" value=\"|s=|ies=y|es=e|es=|ed=e|ed=|ing=e|ing=|\"/>\n" +
        "							<param name=\"adjective\" value=\"|er=|est=|er=e|est=e|\"/>\n" +
        "                            <param name=\"operations\">\n" +
        "                                <param value=\"net.didion.jwnl.dictionary.morph.LookupIndexWordOperation\"/>\n" +
        "                                <param value=\"net.didion.jwnl.dictionary.morph.LookupExceptionsOperation\"/>\n" +
        "                            </param>\n" +
        "						</param>\n" +
        "					</param>\n" +
        "				</param>\n" +
        "			</param>\n" +
        "		</param>\n" +
        "		<param name=\"dictionary_element_factory\" value=\"net.didion.jwnl.princeton.data.PrincetonWN17FileDictionaryElementFactory\"/>\n" +
        "		<param name=\"file_manager\" value=\"net.didion.jwnl.dictionary.file_manager.FileManagerImpl\">\n" +
        "			<param name=\"file_type\" value=\"net.didion.jwnl.princeton.file.PrincetonRandomAccessDictionaryFile\"/>\n" +
        "			<param name=\"dictionary_path\" value=\""+ pathToWordnet +"\"/>\n" +
        "		</param>\n" +
        "	</dictionary>\n" +
        "	<resource class=\"PrincetonResource\"/>\n" +
        "</jwnl_properties>";
        
        return jwnlXML;
    }
}
