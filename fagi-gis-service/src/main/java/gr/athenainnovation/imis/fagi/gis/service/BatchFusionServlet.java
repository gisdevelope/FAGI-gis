/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.athenainnovation.imis.fagi.gis.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Maps;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.shared.PrefixMapping;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.update.UpdateExecutionFactory;
import com.hp.hpl.jena.update.UpdateFactory;
import com.hp.hpl.jena.update.UpdateProcessor;
import com.hp.hpl.jena.update.UpdateRequest;
import gr.athenainnovation.imis.fusion.gis.core.Link;
import gr.athenainnovation.imis.fusion.gis.geotransformations.AbstractFusionTransformation;
import gr.athenainnovation.imis.fusion.gis.geotransformations.KeepBothTransformation;
import gr.athenainnovation.imis.fusion.gis.geotransformations.KeepLeftTransformation;
import gr.athenainnovation.imis.fusion.gis.geotransformations.KeepRightTransformation;
import gr.athenainnovation.imis.fusion.gis.geotransformations.ShiftAToB;
import gr.athenainnovation.imis.fusion.gis.geotransformations.ShiftBToA;
import gr.athenainnovation.imis.fusion.gis.geotransformations.KeepLeftTransformation;
import gr.athenainnovation.imis.fusion.gis.geotransformations.KeepRightTransformation;
import gr.athenainnovation.imis.fusion.gis.geotransformations.KeepBothTransformation;
import gr.athenainnovation.imis.fusion.gis.gui.FuserPanel;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import gr.athenainnovation.imis.fusion.gis.gui.workers.Dataset;
import gr.athenainnovation.imis.fusion.gis.gui.workers.GraphConfig;
import gr.athenainnovation.imis.fusion.gis.utils.Constants;
import gr.athenainnovation.imis.fusion.gis.utils.Log;
import gr.athenainnovation.imis.fusion.gis.utils.SPARQLUtilities;
import gr.athenainnovation.imis.fusion.gis.utils.Utilities;
import static gr.athenainnovation.imis.fusion.gis.utils.Utilities.isURLToLocalInstance;
import gr.athenainnovation.imis.fusion.gis.virtuoso.VirtuosoImporter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.web.auth.HttpAuthenticator;
import org.apache.jena.atlas.web.auth.SimpleAuthenticator;
import virtuoso.jdbc4.VirtuosoConnection;
import virtuoso.jdbc4.VirtuosoException;
import virtuoso.jdbc4.VirtuosoPreparedStatement;
import virtuoso.jdbc4.VirtuosoResultSet;
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoUpdateFactory;
import virtuoso.jena.driver.VirtuosoUpdateRequest;

/**
 * Batch fusion of geometric and other metadata
 * The servlet returns fusion results stored in JSON format
 *
 * @author Nick Vitsas
 */
@WebServlet(name = "BatchFusionServlet", urlPatterns = {"/BatchFusionServlet"})
public class BatchFusionServlet extends HttpServlet {
    
    private static final org.apache.log4j.Logger LOG = Log.getClassFAGILogger(BatchFusionServlet.class);   
    
    private static final double OFFSET_EPSILON = 0.000000000001;

    private class JSONBatchFusions {
        List<JSONBatchPropertyFusion> fusions;

        public JSONBatchFusions(List<JSONBatchPropertyFusion> fusions) {
            this.fusions = fusions;
        }

                
        public JSONBatchFusions() {
            fusions = new ArrayList<>();
        }

        public List<JSONBatchPropertyFusion> getFusions() {
            return fusions;
        }

        public void setFusions(List<JSONBatchPropertyFusion> fusions) {
            this.fusions = fusions;
        }
        
    }
    
    private static class JSONShiftFactors {
        Float shift;
        Float scaleFact;
        Float rotateFact;
        Float gOffsetAX;
        Float gOffsetAY;
        Float gOffsetBX;
        Float gOffsetBY;
        
        public JSONShiftFactors() {
        }

        public Float getShift() {
            return shift;
        }

        public void setShift(Float shift) {
            this.shift = shift;
        }

        public Float getScaleFact() {
            return scaleFact;
        }

        public void setScaleFact(Float scaleFact) {
            this.scaleFact = scaleFact;
        }

        public Float getRotateFact() {
            return rotateFact;
        }

        public void setRotateFact(Float rotateFact) {
            this.rotateFact = rotateFact;
        }

        public Float getgOffsetAX() {
            return gOffsetAX;
        }

        public void setgOffsetAX(Float gOffsetAX) {
            this.gOffsetAX = gOffsetAX;
        }

        public Float getgOffsetAY() {
            return gOffsetAY;
        }

        public void setgOffsetAY(Float gOffsetAY) {
            this.gOffsetAY = gOffsetAY;
        }

        public Float getgOffsetBX() {
            return gOffsetBX;
        }

        public void setgOffsetBX(Float gOffsetBX) {
            this.gOffsetBX = gOffsetBX;
        }

        public Float getgOffsetBY() {
            return gOffsetBY;
        }

        public void setgOffsetBY(Float gOffsetBY) {
            this.gOffsetBY = gOffsetBY;
        }
        
    }
    
    private static class JSONClusterLink {
        String nodeA;
        String nodeB;

        public JSONClusterLink() {
        }

        public String getNodeA() {
            return nodeA;
        }

        public void setNodeA(String nodeA) {
            this.nodeA = nodeA;
        }

        public String getNodeB() {
            return nodeB;
        }

        public void setNodeB(String nodeB) {
            this.nodeB = nodeB;
        }

    }
    
    private static class JSONBatchPropertyFusion {
        String pre;
        String preL;
        String action;

        public JSONBatchPropertyFusion() {
        }

        public String getPre() {
            return pre;
        }

        public void setPre(String pre) {
            this.pre = pre;
        }

        public String getPreL() {
            return preL;
        }

        public void setPreL(String preL) {
            this.preL = preL;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
        
    }
    
    private class JSONFusionResults {
        HashMap<String, JSONFusionResult> fusedGeoms;
        int cluster;

        public JSONFusionResults() {
            fusedGeoms = new HashMap<>();
        }

        public HashMap<String, JSONFusionResult> getFusedGeoms() {
            return fusedGeoms;
        }

        public void setFusedGeoms(HashMap<String, JSONFusionResult> fusedGeoms) {
            this.fusedGeoms = fusedGeoms;
        }

        public int getCluster() {
            return cluster;
        }

        public void setCluster(int cluster) {
            this.cluster = cluster;
        }
        
    }
    
    private class JSONFusionResult {
        String geom;
        String nb;

        public JSONFusionResult() {
        }

        public JSONFusionResult(String geom, String nb) {
            this.geom = geom;
            this.nb = nb;
        }

        public String getGeom() {
            return geom;
        }

        public void setGeom(String geom) {
            this.geom = geom;
        }

        public String getNb() {
            return nb;
        }

        public void setNb(String nb) {
            this.nb = nb;
        }

    }
    
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException {
        response.setContentType("text/html;charset=UTF-8");

        // Per equest state
        DBConfig                        dbConf;
        GraphConfig                     grConf;
        VirtGraph                       vSet = null;
        Connection                      dbConn = null;
        List<FusionState>               fs = null;
        String                          tGraph = null;
        String                          nodeA = null;
        String                          nodeB = null;
        String                          dom = null;
        String                          domSub = null;
        HttpSession                     sess = null;
        JSONBatchPropertyFusion[]       selectedFusions;
        JSONClusterLink[]               clusterLinks = null;
        JSONFusionResults               ret = null;
        int                             activeCluster;
        String                          activeLinkTable = "links";
    
        try (PrintWriter out = response.getWriter()) {
            sess = request.getSession(false);
            
            if ( sess == null ) {
                return;
            }
            
            ret = new JSONFusionResults();
                       
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
            //mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMM yyyy");
            mapper.setDateFormat(outputFormat);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            
            dbConf = (DBConfig)sess.getAttribute("db_conf");
            grConf = (GraphConfig)sess.getAttribute("gr_conf");
            fs = (List<FusionState>)sess.getAttribute("fstate");
            nodeA = (String)sess.getAttribute("nodeA");
            nodeB = (String)sess.getAttribute("nodeB");
            tGraph = (String)sess.getAttribute("t_graph");
            
            try {    
            vSet = new VirtGraph ("jdbc:virtuoso://" + dbConf.getDBURL() + "/CHARSET=UTF-8",
                                         dbConf.getUsername(), 
                                         dbConf.getPassword());
            } catch (JenaException connEx) {
                System.out.println(connEx.getMessage());      
                out.println("Connection to virtuoso failed");
                out.close();
            
                return;
            }
        
            try{
                Class.forName("org.postgresql.Driver");     
            } catch (ClassNotFoundException ex) {
                System.out.println(ex.getMessage());      
                out.println("Class of postgis failed");
                out.close();
            
                return;
            }
             
            try {
                String url = Constants.DB_URL.concat(dbConf.getDBName());
                dbConn = DriverManager.getConnection(url, dbConf.getDBUsername(), dbConf.getDBPassword());
                dbConn.setAutoCommit(false);
            } catch(SQLException sqlex) {
                System.out.println(sqlex.getMessage());      
                out.println("Connection to postgis failed");
                out.close();
            
                return;
            }

            String propsJSON = request.getParameter("propsJSON");
            String shiftJSON = request.getParameter("factJSON");
            String clusterJSON = request.getParameter("clusterJSON");
            
            String domA = (String)sess.getAttribute("domA");
            String domB = (String)sess.getAttribute("domB");
            dom = domB;
            domSub = nodeB;
            if ( grConf.isDominantA() ) {
                domSub = nodeA;
                dom = domA;
            }
            System.out.println("Dom A "+domA+" Dom B "+domB);
            
            JsonFactory factory = mapper.getJsonFactory(); // since 2.1 use mapper.getFactory() instead
            JsonParser jp = factory.createJsonParser(propsJSON);
            selectedFusions = mapper.readValue(jp, JSONBatchPropertyFusion[].class );

            JsonParser jp2 = factory.createJsonParser(shiftJSON);
            JSONShiftFactors sFactors = mapper.readValue(jp2, JSONShiftFactors.class );
            
            activeCluster = Integer.parseInt(request.getParameter("cluster"));
            if ( activeCluster > -1 ) {
                JsonParser jpCluster = factory.createJsonParser(clusterJSON);
                clusterLinks = mapper.readValue(jpCluster, JSONClusterLink[].class );
                System.out.println("Cluster size "+ clusterLinks.length);
                
                activeLinkTable = "cluster";
                loadClusterLinks(clusterLinks, dbConn);
                
                dbConn.commit();
            }
            
            System.out.println(selectedFusions[0].preL);
            
            AbstractFusionTransformation trans = null;
            boolean skipFusion = false;
            for(;;) {
                trans = FuserPanel.transformations.get(selectedFusions[0].action);
                if ( trans instanceof ShiftAToB) {
                    ((ShiftAToB)trans).setShift(sFactors.shift);
                    ((ShiftAToB)trans).setRotate(sFactors.rotateFact);
                    ((ShiftAToB)trans).setScale(sFactors.scaleFact);
                }
            
                if ( trans instanceof ShiftBToA) {
                    ((ShiftBToA)trans).setShift(sFactors.shift);
                    ((ShiftBToA)trans).setRotate(sFactors.rotateFact);
                    ((ShiftBToA)trans).setScale(sFactors.scaleFact);
                }
                
                if ( trans instanceof KeepLeftTransformation) {
                    System.out.println(sFactors.getgOffsetAX());
                    System.out.println(sFactors.getgOffsetAY());
                    System.out.println(sFactors.getgOffsetBX());
                    System.out.println(sFactors.getgOffsetBY());
                    
                    if (Math.abs(sFactors.getgOffsetAX()) > OFFSET_EPSILON
                            || Math.abs(sFactors.getgOffsetAY()) > OFFSET_EPSILON) {
                        offsetGeometriesA("dataset_a_geometries", activeLinkTable, -sFactors.getgOffsetAX(), -sFactors.getgOffsetAY(), dbConn);
                        
                        skipFusion = true;
                    }
                }
                
                if ( trans instanceof KeepRightTransformation) {
                    System.out.println(sFactors.getgOffsetAX());
                    System.out.println(sFactors.getgOffsetAY());
                    System.out.println(sFactors.getgOffsetBX());
                    System.out.println(sFactors.getgOffsetBY());
                    
                    if (Math.abs(sFactors.getgOffsetBX()) > OFFSET_EPSILON
                            || Math.abs(sFactors.getgOffsetBY()) > OFFSET_EPSILON) {
                        offsetGeometriesB("dataset_b_geometries", activeLinkTable, -sFactors.getgOffsetBX(), -sFactors.getgOffsetBY(), dbConn);
                        
                        skipFusion = true;
                    }
                }
                
                if ( trans instanceof KeepBothTransformation) {
                    System.out.println(sFactors.getgOffsetAX());
                    System.out.println(sFactors.getgOffsetAY());
                    System.out.println(sFactors.getgOffsetBX());
                    System.out.println(sFactors.getgOffsetBY());
                    
                    if (Math.abs(sFactors.getgOffsetBX()) > OFFSET_EPSILON
                            || Math.abs(sFactors.getgOffsetBY()) > OFFSET_EPSILON
                            || Math.abs(sFactors.getgOffsetAX()) > OFFSET_EPSILON
                            || Math.abs(sFactors.getgOffsetAY()) > OFFSET_EPSILON) {
                        offsetGeometriesA("dataset_a_geometries", activeLinkTable, -sFactors.getgOffsetAX(), -sFactors.getgOffsetAY(), dbConn);
                        offsetGeometriesB("dataset_b_geometries", activeLinkTable, -sFactors.getgOffsetBX(), -sFactors.getgOffsetBY(), dbConn);
                    
                        skipFusion = true;
                    }
                }
                System.out.println(trans == null);
                
                if (!skipFusion) {
                    if (activeCluster > -1) {
                        trans.fuseCluster(dbConn);
                    } else {
                        //System.out.println("Fusing links");
                        trans.fuseAll(dbConn);
                    }
                }
                
                String queryGeoms = "SELECT b.subject_a, b.subject_b as lb, ST_asText(b.geom) as g\n" +
                                 "FROM fused_geometries AS b\n";
            
                PreparedStatement stmt = dbConn.prepareStatement(queryGeoms);
                ResultSet rs = stmt.executeQuery();
            
                //System.out.println(request.getParameter("cluster"));
                ret.setCluster(activeCluster);
                String subject;
                String subjectB;
                StringBuilder geom = new StringBuilder();
                
                while (rs.next()) {
                    JSONFusionResult res = new JSONFusionResult(rs.getString(3), rs.getString(2));
                    ret.getFusedGeoms().put(rs.getString(1), res);
                }
                
                rs.close();
                stmt.close();
                
                VirtuosoImporter virtImp = (VirtuosoImporter)sess.getAttribute("virt_imp");
                virtImp.setTransformationID(trans.getID());
            
                //virtImp.importGeometriesToVirtuoso((String)sess.getAttribute("t_graph"));
                virtImp.importGeometriesToVirtuoso(grConf.getTargetTempGraph());
            
                //virtImp.trh.finish();
                virtImp.finishUpload();
                
                break;
            }
            
            // If the Target dataset is equal to one of the other two perform DELETEs
            if ( ( grConf.getEndpointA().equalsIgnoreCase(grConf.getEndpointT())
                    && grConf.getGraphA().equals(tGraph) )
                    || ( grConf.getEndpointB().equalsIgnoreCase(grConf.getEndpointT())
                    && grConf.getGraphB().equals(tGraph) ) ) {
                for (int i = 1; i < selectedFusions.length; i++) {
                    eraseOldMetadata(selectedFusions[i].action, i, tGraph, sess, grConf, vSet, selectedFusions );
                }
            }
            
            // Create insertion statements
            List<VirtuosoPreparedStatement> stmts = new ArrayList<>();
            for ( int i = 0; i < 4; i++ ) {
                StringBuilder sb = new StringBuilder();
                sb.append("SPARQL WITH <"+tGraph+"_"+dbConf.getDBName()+"_fagi"+"> INSERT {");
                for ( int p = 0; p < i; p++ ) {
                    sb.append("`iri(??)`  `iri(??)` `iri(??)` .");
                }
                sb.append("`iri(??)`  `iri(??)` ?? . } ");
                System.out.println("Statement " + sb.toString());
                VirtuosoConnection conn = (VirtuosoConnection) vSet.getConnection();
                VirtuosoPreparedStatement vstmt = (VirtuosoPreparedStatement) conn.prepareStatement(sb.toString());
                
                stmts.add(vstmt);
            }
            
            List<Link> linkList = (List<Link>) sess.getAttribute("links_list");
            int lastIndex = 0;
            do {
                System.out.println("Running link creation loop " + linkList.size());
                if ( activeCluster > -1 ) 
                    lastIndex = createClusterGraph(clusterLinks, lastIndex, grConf, vSet);
                else 
                    lastIndex = createLinksGraphBatch(linkList, lastIndex, grConf, vSet);
                                
                // Perform Metadata Fusion
                for (int i = 1; i < selectedFusions.length; i++) {
                    if (Constants.LATE_FETCH) {
                        lateFetchData(i, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
                    }
                    handleMetadataFusion(selectedFusions[i].action, i, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
                }

            } while ( lastIndex != 0);
            
            //SPARQLUtilities.clearFusedLinks(grConf, activeCluster, vSet.getConnection());
            
            System.out.println(mapper.writeValueAsString(ret));
            
            // Update destinATION GRAPH
            System.out.println("\n\n\n\n\nPreparing to update remote endpoint\n\n\n");
            UpdateRemoteEndpoint(grConf, vSet);
            System.out.println("\n\n\n\n\nFiniished updating remote endpoint\n\n\n");
            out.println(mapper.writeValueAsString(ret));
            
        } finally {
            if ( vSet != null ) 
                vSet.close();
            
            if ( dbConn != null ) 
                dbConn.close();
        }
    }
    
    private void lateFetchData(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {
        //System.out.println("\n\n\n\n\nLATE FETCHING\n\n\n");
        long startTime, endTime;
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lstA = (List<String>) sess.getAttribute("property_patternsA");
        List<String> lstB = (List<String>) sess.getAttribute("property_patternsB");

        if (grConf.isDominantA()) {
            domOnto = (String) sess.getAttribute("domA");
        } else {
            domOnto = (String) sess.getAttribute("domB");
        }
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;

        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if (newPredTokes.length == 2) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",", "_");
        System.out.println("Long name : " + longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres " + leftPre);
        for (String s : leftPres) {
            System.out.print(s + " ");
        }
        System.out.println();
        System.out.println("Right pres " + rightPre);
        for (String s : rightPres) {
            System.out.print(s + " ");
        }

        StringBuilder getFromB = new StringBuilder();
        StringBuilder getFromA = new StringBuilder();

        final String clearMetaAGraph = "SPARQL CLEAR GRAPH <" + grConf.getMetadataGraphA() + ">";
        final String clearMetaBGraph = "SPARQL CLEAR GRAPH <" + grConf.getMetadataGraphB() + ">";

        try (PreparedStatement clearMetaAGraphStmt = virt_conn.prepareStatement(clearMetaAGraph);
                PreparedStatement clearMetaBGraphStmt = virt_conn.prepareStatement(clearMetaBGraph)) {

            clearMetaAGraphStmt.execute();
            clearMetaBGraphStmt.execute();

        } catch (SQLException ex) {

            LOG.trace("SQLException thrown during temp graph creation");
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getMessage());
            LOG.debug("SQLException thrown during temp graph creation : " + ex.getSQLState());

        }

        //testThreads(links);
        //LOG.info(ANSI_YELLOW + "Thread test lasted " + ((endtime - starttime) / 1000000000f) + "" + ANSI_RESET);
        boolean isEndpointALocal;
        boolean isEndpointBLocal;

        isEndpointALocal = isURLToLocalInstance(grConf.getEndpointA()); //"localhost" for localhost

        isEndpointBLocal = isURLToLocalInstance(grConf.getEndpointB()); //"localhost" for localhost

        for (String rightProp : rightPres) {
            //String[] leftPreTokens = leftPre.split(",");
            //String[] rightPreTokens = rightPre.split(",");
            String[] mainPattern = rightProp.split(",");

            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(rightProp, lstB);

            StringBuilder q = new StringBuilder();
            String prev_s = "";
            System.out.println("Patterns " + patterns);

            for (String pattern : patterns) {
                q.setLength(0);

                String[] rightPreTokens = pattern.split(",");

                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + rightPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                getFromB.append("SPARQL INSERT\n");
                getFromB.append("  { GRAPH <").append(grConf.getMetadataGraphB()).append("> {\n");
                if (grConf.isDominantA()) {
                    getFromB.append(" ?s <" + rightPreTokens[0] + "> ?o0 . \n");
                } else {
                    getFromB.append(" ?o <" + rightPreTokens[0] + "> ?o0 . \n");
                }
                prev_s = "?o0";
                for (int i = 1; i < rightPreTokens.length; i++) {
                    getFromB.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                getFromB.append("} }\nWHERE\n");
                getFromB.append("{\n");
                getFromB.append(" GRAPH <" + grConf.getLinksGraph()+ "> { ");
                if (grConf.isDominantA()) {
                    getFromB.append(" ?o ?same ?s } .\n");
                } else {
                    getFromB.append(" ?s ?same ?o } .\n");
                }
                if (isEndpointALocal) {
                    getFromB.append(" GRAPH <").append(grConf.getGraphB()).append("> {\n");
                    getFromB.append(" ?s <" + rightPreTokens[0] + "> ?o0 . \n");
                    prev_s = "?o0";
                    for (int i = 1; i < rightPreTokens.length; i++) {
                        getFromB.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                        prev_s = "?o" + i;
                    }
                    getFromB.append(" }\n");
                } else {
                    getFromB.append(" SERVICE <" + grConf.getEndpointB() + "> { GRAPH <").append(grConf.getGraphB()).append("> { \n");
                    getFromB.append(" ?o <" + rightPreTokens[0] + "> ?o0 . \n");
                    prev_s = "?o0";
                    for (int i = 1; i < rightPreTokens.length; i++) {
                        getFromA.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                        prev_s = "?o" + i;
                    }
                    getFromB.append(" } }\n");
                }
                getFromB.append("}");

                System.out.println("LATE FETCH " + getFromB.toString());
                
                int tries = 0;
                startTime = System.nanoTime();
                while (tries < Constants.MAX_SPARQL_TRIES) {
                    try (PreparedStatement populateDataB = virt_conn.prepareStatement(getFromB.toString())) {

                        populateDataB.executeUpdate();

                    } catch (SQLException ex) {

                        LOG.trace("SQLException thrown during temp graph populating");
                        LOG.debug("SQLException thrown during temp graph populating Try : " + (tries + 1));
                        LOG.debug("SQLException thrown during temp graph populating : " + ex.getMessage());
                        LOG.debug("SQLException thrown during temp graph populating : " + ex.getSQLState());

                        tries++;
                    }
                }
                endTime = System.nanoTime();
                    
                LOG.info("Uploading B lasted "+Utilities.nanoToSeconds(endTime-startTime));
                    
            }
        }
        
        for (String leftProp : leftPres) {
            //String[] leftPreTokens = leftPre.split(",");
            //String[] rightPreTokens = rightPre.split(",");
            String[] mainPattern = leftProp.split(",");

            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(leftProp, lstA);

            StringBuilder q = new StringBuilder();
            String prev_s = "";
            System.out.println("Patterns " + patterns);

            for (String pattern : patterns) {
                q.setLength(0);

                String[] leftPreTokens = pattern.split(",");

                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + leftPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                getFromA.append("SPARQL INSERT\n");
                getFromA.append("  { GRAPH <").append(grConf.getMetadataGraphA()).append("> {\n");
                if (grConf.isDominantA()) {
                    getFromA.append(" ?s <" + leftPreTokens[0] + "> ?o0 . \n");
                } else {
                    getFromA.append(" ?o <" + leftPreTokens[0] + "> ?o0 . \n");
                }
                prev_s = "?o0";
                for (int i = 1; i < leftPreTokens.length; i++) {
                    getFromA.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                getFromA.append("} }\nWHERE\n");
                getFromA.append("{\n");
                getFromA.append(" GRAPH <" + grConf.getLinksGraph()+ "> { ");
                if (grConf.isDominantA()) {
                    getFromA.append(" ?s ?same ?o } .\n");
                } else {
                    getFromA.append(" ?o ?same ?s } .\n");
                }
                if (isEndpointALocal) {
                    getFromA.append(" GRAPH <").append(grConf.getGraphA()).append("> {\n");
                    getFromA.append(" ?s <" + leftPreTokens[0] + "> ?o0 . \n");
                    prev_s = "?o0";
                    for (int i = 1; i < leftPreTokens.length; i++) {
                        getFromA.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                        prev_s = "?o" + i;
                    }
                    getFromA.append(" }\n");
                } else {
                    getFromA.append(" SERVICE <" + grConf.getEndpointA() + "> { GRAPH <").append(grConf.getGraphA()).append("> { \n");
                    getFromA.append(" ?o <" + leftPreTokens[0] + "> ?o0 . \n");
                    prev_s = "?o0";
                    for (int i = 1; i < leftPreTokens.length; i++) {
                        getFromA.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                        prev_s = "?o" + i;
                    }
                    getFromA.append(" } }\n");
                }
                getFromA.append("}");

                System.out.println("LATE FETCH " + getFromA.toString());
                
                // Populate with data from the Sample Liink set
                int tries = 0;
                startTime = System.nanoTime();
                while (tries < Constants.MAX_SPARQL_TRIES) {
                    try (PreparedStatement populateDataA = virt_conn.prepareStatement(getFromA.toString())) {

                        populateDataA.executeUpdate();

                    } catch (SQLException ex) {

                        LOG.trace("SQLException thrown during temp graph populating");
                        LOG.debug("SQLException thrown during temp graph populating Try : " + (tries + 1));
                        LOG.debug("SQLException thrown during temp graph populating : " + ex.getMessage());
                        LOG.debug("SQLException thrown during temp graph populating : " + ex.getSQLState());

                        tries++;
                    }
                }
                endTime = System.nanoTime();
                    
                LOG.info("Uploading A lasted "+Utilities.nanoToSeconds(endTime-startTime));
            }
        }
    }
     
    private void loadClusterLinks(final JSONClusterLink[] links, Connection dbConn) throws SQLException {
        
        //delete old geometries from fused_geometries table. 
        try{
            
           String deleteLinksTable = "DELETE FROM cluster";
           PreparedStatement statement = dbConn.prepareStatement(deleteLinksTable); 
           statement.executeUpdate(); //jan 
           
           statement.close();
           
           dbConn.commit();
           
        }
        catch (SQLException ex)
        {
          dbConn.rollback();  
          ex.printStackTrace();
          ex.getNextException().printStackTrace();
          //LOG.warn(ex.getMessage(), ex);
        }
        String insertLinkQuery = "INSERT INTO cluster (nodea, nodeb) VALUES (?,?)"; 
        final PreparedStatement insertLinkStmt = dbConn.prepareStatement(insertLinkQuery);
        
        for(JSONClusterLink link : links) {         
            insertLinkStmt.setString(1, link.getNodeA());
            insertLinkStmt.setString(2, link.getNodeB());
            
            insertLinkStmt.addBatch();
        }    
        insertLinkStmt.executeBatch();
        
        insertLinkStmt.close();
        
        dbConn.commit();
    }
    
    private int createClusterGraph(JSONClusterLink[] cluster, int startIndex, GraphConfig grConf, VirtGraph vSet) throws VirtuosoException, BatchUpdateException {
        StringBuilder sb = new StringBuilder();
        final String dropGraph = "sparql DROP SILENT GRAPH <"+ grConf.getClusterGraph()+  ">";
        final String createGraph = "sparql CREATE GRAPH <"+ grConf.getClusterGraph()+ ">";
        VirtuosoConnection conn = (VirtuosoConnection) vSet.getConnection();

        VirtuosoPreparedStatement dropStmt;
        dropStmt = (VirtuosoPreparedStatement)conn.prepareStatement(dropGraph);
        dropStmt.execute();

        dropStmt.close();
        
        VirtuosoPreparedStatement createStmt;
        createStmt = (VirtuosoPreparedStatement)conn.prepareStatement(createGraph);
        createStmt.execute();
        
        createStmt.close();
        
        sb.append("SPARQL WITH <"+ grConf.getClusterGraph()+"> INSERT {");
        sb.append("`iri(??)` <"+Constants.SAME_AS+"> `iri(??)` . } ");
        System.out.println("Statement " + sb.toString());
        VirtuosoPreparedStatement vstmt = (VirtuosoPreparedStatement) conn.prepareStatement(sb.toString());
                
        int start = startIndex;
        int end = startIndex + Constants.BATCH_SIZE;
        if ( end > cluster.length ) {
            end = cluster.length;
        }
        
        for ( int i = start; i < end; ++i ) {
            JSONClusterLink link = cluster[i];
            vstmt.setString(1, link.getNodeA());
            vstmt.setString(2, link.getNodeB());
            
            vstmt.addBatch();
        }
        
        vstmt.executeBatch();
        
        vstmt.close();
        
        if ( end == cluster.length )
            return 0;
        else 
            return end;

    }
    
    private void createClusterGraph(JSONClusterLink[] cluster, DBConfig dbConf, VirtGraph vSet) throws VirtuosoException, BatchUpdateException {
        StringBuilder sb = new StringBuilder();
        sb.append("SPARQL WITH <http://localhost:8890/DAV/all_cluster_" + dbConf.getDBName()+"> INSERT {");
        sb.append("`iri(??)` <"+Constants.SAME_AS+"> `iri(??)` . } ");
        System.out.println("Statement " + sb.toString());
        VirtuosoConnection conn = (VirtuosoConnection) vSet.getConnection();
        VirtuosoPreparedStatement vstmt = (VirtuosoPreparedStatement) conn.prepareStatement(sb.toString());
                
        int start = 0;
        int end = cluster.length;
        
        for ( int i = start; i < end; ++i ) {
            JSONClusterLink link = cluster[i];
            vstmt.setString(1, link.getNodeA());
            vstmt.setString(2, link.getNodeB());
            
            vstmt.addBatch();
        }
        
        vstmt.executeBatch();
        
        vstmt.close();
    }
    
    private void sendEntities(GraphConfig gc) {
        
    }
    
    private void eraseOldMetadata(String action, int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions) throws SQLException, UnsupportedEncodingException {
        //String s2 = "SPARQL WITH <http://localhost:8890/DAV/osm_demo_asasas> DELETE { `iri(??)` `iri(??)` ?? }";
        //String s = "SPARQL SELECT * WHERE { ?? ?p ?o  FILTER ( isLiTERAL ( ?o ) ) } LIMIT 10";
        VirtuosoConnection conn = (VirtuosoConnection) vSet.getConnection();
        VirtuosoPreparedStatement stmt = null;
        conn.setAutoCommit(false);
        String domOnto = "";
        List<String> lstA = (List<String>)sess.getAttribute("property_patternsA");
        List<String> lstB = (List<String>)sess.getAttribute("property_patternsB");

                
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        List<Link> l = (List<Link>) sess.getAttribute("links_list");
        
        for (String leftProp : leftPres) {
            //String[] leftPreTokens = rightProp.split(",");
            String[] mainPattern = leftProp.split(",");

            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(leftProp, lstA);

            System.out.println("Patterns " + patterns);

            for (String pattern : patterns) {
                String[] leftPreTokens = pattern.split(",");
                boolean updating = true;
                int addIdx = 0;
                int cSize = 1;
                int sizeUp = 1;
                StringBuilder queryStrWhere = new StringBuilder();
                while (updating) {
                    try {
                        ParameterizedSparqlString queryStr = new ParameterizedSparqlString();
                        queryStrWhere.setLength(0);
                        //queryStr.append("WITH <"+fusedGraph+"> ");
                        queryStr.append("DELETE { ");
                        queryStr.append("GRAPH <" + tGraph + "> { ");
                        int top = 0;
                        if (cSize >= l.size()) {
                            top = l.size();
                        } else {
                            top = cSize;
                        }
                        for (int i = addIdx; i < top; i++) {
                            final String subject = l.get(i).getNodeA();
                            final String subjectB = l.get(i).getNodeB();
                            
                            
                            String prev_s = "<" + subject + ">";
                            for (int count = 0; count < leftPreTokens.length; count++) {
                                queryStr.append(prev_s);
                                queryStr.append(" ");
                                queryStr.appendIri(leftPreTokens[count]);
                                queryStr.append(" ");
                                queryStr.append("?o"+i+""+count);
                                queryStr.append(" ");
                                queryStr.append(".");
                                queryStr.append(" ");
                                
                                queryStrWhere.append(prev_s);
                                queryStrWhere.append(" ");
                                queryStrWhere.append("<"+leftPreTokens[count]+">");
                                queryStrWhere.append(" ");
                                queryStrWhere.append("?o"+i+""+count);
                                queryStrWhere.append(" ");
                                queryStrWhere.append(".");
                                queryStrWhere.append(" ");
                                prev_s = "?o"+i+""+count;
                            }
                            
                        }
                        queryStr.append("} } WHERE {");
                        queryStr.append("GRAPH <" + tGraph + "> { ");
                        queryStr.append(queryStrWhere.toString());
                        queryStr.append("} } ");
                        System.out.println("Print "+queryStr.toString());

                        //UpdateRequest q = queryStr.asUpdate();
                        UpdateRequest q = UpdateFactory.create(queryStr.toString());
                        HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
                        UpdateProcessor insertRemoteB = UpdateExecutionFactory.createRemoteForm(q, grConf.getEndpointT(), authenticator);
                        insertRemoteB.execute();

                        //VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(queryStr.toString(), set);

                        //update_handler.addUpdate(updateQuery);
                        //vur.exec();

                        //System.out.println("Add at "+addIdx+" Size "+cSize);
                        addIdx += (cSize - addIdx);
                        sizeUp *= 2;
                        cSize += sizeUp;
                        if (cSize >= l.size()) {
                            cSize = l.size();
                        }
                        if (cSize == addIdx) {
                            updating = false;
                        }
                    } catch (org.apache.jena.atlas.web.HttpException ex) {
                        System.out.println("Failed at " + addIdx + " Size " + cSize);
                        System.out.println("Crazy Stuff");
                        System.out.println(ex.getLocalizedMessage());
                        ex.printStackTrace();
                        ex.printStackTrace(System.out);
                        sizeUp = 1;
                        cSize = addIdx;
                        cSize += sizeUp;
                        if (cSize >= l.size()) {
                            cSize = l.size();
                        }
                        //System.out.println("Going back at "+addIdx+" Size "+cSize);

                        break;
                        //System.out.println("Going back at "+addIdx+" Size "+cSize);
                    } catch (Exception ex) {
                        System.out.println(ex.getLocalizedMessage());
                        break;
                    }
                }
            }
        }
        
        for (String rightProp : rightPres) {
            //String[] leftPreTokens = rightProp.split(",");
            String[] mainPattern = rightProp.split(",");

            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(rightProp, lstB);

            System.out.println("Patterns " + patterns);

            for (String pattern : patterns) {
                String[] rightPreTokens = pattern.split(",");
                boolean updating = true;
                int addIdx = 0;
                int cSize = 1;
                int sizeUp = 1;
                StringBuilder queryStrWhere = new StringBuilder();
                while (updating) {
                    try {
                        ParameterizedSparqlString queryStr = new ParameterizedSparqlString();
                        queryStrWhere.setLength(0);
                        //queryStr.append("WITH <"+fusedGraph+"> ");
                        queryStr.append("DELETE { ");
                        queryStr.append("GRAPH <" + tGraph + "> { ");
                        int top = 0;
                        if (cSize >= l.size()) {
                            top = l.size();
                        } else {
                            top = cSize;
                        }
                        for (int i = addIdx; i < top; i++) {
                            final String subject = l.get(i).getNodeA();
                            final String subjectB = l.get(i).getNodeB();
                            
                            
                            String prev_s = "<" + subject + ">";
                            for (int count = 0; count < rightPreTokens.length; count++) {
                                queryStr.append(prev_s);
                                queryStr.append(" ");
                                queryStr.appendIri(rightPreTokens[count]);
                                queryStr.append(" ");
                                queryStr.append("?o"+i+""+count);
                                queryStr.append(" ");
                                queryStr.append(".");
                                queryStr.append(" ");
                                
                                queryStrWhere.append(prev_s);
                                queryStrWhere.append(" ");
                                queryStrWhere.append("<"+rightPreTokens[count]+">");
                                queryStrWhere.append(" ");
                                queryStrWhere.append("?o"+i+""+count);
                                queryStrWhere.append(" ");
                                queryStrWhere.append(".");
                                queryStrWhere.append(" ");
                                prev_s = "?o"+i+""+count;
                            }
                            
                        }
                        queryStr.append("} } WHERE {");
                        queryStr.append("GRAPH <" + tGraph + "> { ");
                        queryStr.append(queryStrWhere.toString());
                        queryStr.append("} } ");
                        System.out.println("Print "+queryStr.toString());

                        //UpdateRequest q = queryStr.asUpdate();
                        UpdateRequest q = UpdateFactory.create(queryStr.toString());
                        HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
                        UpdateProcessor insertRemoteB = UpdateExecutionFactory.createRemoteForm(q, grConf.getEndpointT(), authenticator);
                        insertRemoteB.execute();

                        //VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(queryStr.toString(), set);

                        //update_handler.addUpdate(updateQuery);
                        //vur.exec();

                        //System.out.println("Add at "+addIdx+" Size "+cSize);
                        addIdx += (cSize - addIdx);
                        sizeUp *= 2;
                        cSize += sizeUp;
                        if (cSize >= l.size()) {
                            cSize = l.size();
                        }
                        if (cSize == addIdx) {
                            updating = false;
                        }
                    } catch (org.apache.jena.atlas.web.HttpException ex) {
                        System.out.println("Failed at " + addIdx + " Size " + cSize);
                        System.out.println("Crazy Stuff");
                        System.out.println(ex.getLocalizedMessage());
                        ex.printStackTrace();
                        ex.printStackTrace(System.out);
                        sizeUp = 1;
                        cSize = addIdx;
                        cSize += sizeUp;
                        if (cSize >= l.size()) {
                            cSize = l.size();
                        }
                        //System.out.println("Going back at "+addIdx+" Size "+cSize);

                        break;
                        //System.out.println("Going back at "+addIdx+" Size "+cSize);
                    } catch (Exception ex) {
                        System.out.println(ex.getLocalizedMessage());
                        break;
                    }
                }
            }
        }
           
        //Commit changes
        conn.commit();
        // Return to auto commit for next actions
        conn.setAutoCommit(true);
        
    }
    
    private void handleMetadataFusion(String action, int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {
        if (action.equals("Keep A")) {
            metadataKeepLeft(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep B")) {
            metadataKeepRight(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Both")) {
            //metadataKeepBoth(idx, tGraph, sess, grConf, vSet, selectedFusions);
            metadataKeepRight(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
            metadataKeepLeft(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Concatenated B")) {
            metadataKeepConcatRight(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Concatenated A")) {
            metadataKeepConcatLeft(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Concatenated Both")) {
            metadataKeepConcatLeft(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
            metadataKeepConcatRight(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Concatenation")) {
            metadataConcatenation(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Flattened B")) {
            metadataKeepFlatRight(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Flattened A")) {
            metadataKeepFlatLeft(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
        }
        if (action.equals("Keep Flattened Both")) {
            metadataKeepFlatLeft(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);
            metadataKeepFlatRight(idx, tGraph, sess, grConf, vSet, selectedFusions, activeCluster);  
        }
    }
    
    private void clearPrevious(String[] l, String[] r) {
        /*System.out.println("\n\n\nCLEARING\n\n\n\n");
        //System.out.println("Left " + l);
        //System.out.println("Right " + r);
        
        if ( l == null && r == null ) {
            System.out.println("\n\n\nCLEARING\n\n\n\n");
            return;
        }
        
        ParameterizedSparqlString queryStr = null;
    
        if ( l != null ) {
            for (String s : l) {
                String[] toks = s.split(",");
                String prev_s = "<" + nodeA +">";
                int index = 0;
                
                queryStr = new ParameterizedSparqlString();
                queryStr.append("DELETE { GRAPH <" + tGraph + "> { ");
                for (String tok : toks) {
                    queryStr.append(prev_s);
                    queryStr.append(" ");
                    queryStr.appendIri(tok);
                    queryStr.append(" ");
                    queryStr.append("?o"+index);
                    queryStr.append(" ");
                    queryStr.append(". ");
                    prev_s = "?o"+index;
                    index++;
                }
                queryStr.append("} } WHERE { GRAPH <" + tGraph + "> { ");
                prev_s = "<" + nodeA +">";
                for (String tok : toks) {
                    queryStr.append(prev_s);
                    queryStr.append(" ");
                    queryStr.appendIri(tok);
                    queryStr.append(" ");
                    queryStr.append("?o"+index);
                    queryStr.append(" ");
                    queryStr.append(". ");
                    prev_s = "?o"+index;
                    index++;
                }
                queryStr.append("} }");
            }
        }
         
        if ( queryStr != null ) {
            System.out.println("Deletes A " + queryStr.toString());
            UpdateRequest q = queryStr.asUpdate();
            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            UpdateProcessor delPrev = UpdateExecutionFactory.createRemoteForm(q, grConf.getEndpointT(), authenticator);
            delPrev.execute();
        }
        
        queryStr = null;
        
        if ( r != null ) {
            for (String s : r) {
                String[] toks = s.split(",");
                String prev_s = "<" + nodeA +">";
                int index = 0;
                
                queryStr = new ParameterizedSparqlString();
                queryStr.append("DELETE { GRAPH <" + tGraph + "> { ");
                for (String tok : toks) {
                    queryStr.append(prev_s);
                    queryStr.append(" ");
                    queryStr.appendIri(tok);
                    queryStr.append(" ");
                    queryStr.append("?o"+index);
                    queryStr.append(" ");
                    queryStr.append(". ");
                    prev_s = "?o"+index;
                    index++;
                }
                queryStr.append("} } WHERE { GRAPH <" + tGraph + "> { ");
                prev_s = "<" + nodeA +">";
                for (String tok : toks) {
                    queryStr.append(prev_s);
                    queryStr.append(" ");
                    queryStr.appendIri(tok);
                    queryStr.append(" ");
                    queryStr.append("?o"+index);
                    queryStr.append(" ");
                    queryStr.append(". ");
                    prev_s = "?o"+index;
                    index++;
                }
                queryStr.append("} }");
            }
        }  
        
        if ( queryStr != null ) {
            System.out.println("Deletes B " + queryStr.toString());
            UpdateRequest q = queryStr.asUpdate();
            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            UpdateProcessor delPrev = UpdateExecutionFactory.createRemoteForm(q, grConf.getEndpointT(), authenticator);
            //delPrev.execute();
        }
        
        System.out.println("\n\n\nCLEARING\n\n\n\n");
        */

    }
    
     /**
     * Function that handles metadata fusion
     * based on the selected action. A lot could be done
     * to optimize this single link version but
     * since this is relatively fast, the process is
     * implemented similarly to the batch fusion model for cinsistency
     *
     * @param action String represantation of the selected action
     * @param response servlet response
     * @throws SQLException if a servlet-specific error occurs
     * @throws UnsupportedEncodingException if an I/O error occurs
     */
    private void metadataKeepFlatLeft(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lst = (List<String>)sess.getAttribute("property_patternsA");
        
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        // Find Least Common Prefix
        int lcpIndex = 0;
        String lcpProperty = "";
        Map<String, Map.Entry<Integer, Integer> > commonPres = new HashMap<>();
        for (String leftProp : leftPres) {
            String[] leftPreTokens = leftProp.split(",");
            //String[] rightPreTokens = rightProp.split(",");

            // Find LCP (Least Common Prefix)
            for (int i = 0; i < leftPreTokens.length; i++) {
                if (!commonPres.containsKey(leftPreTokens[i])) {
                    commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(i, 1));
                } else {
                    Map.Entry retValue = commonPres.get(leftPreTokens[i]);
                    int pos = (Integer) retValue.getKey();
                    int val = (Integer) retValue.getValue();
                    if (pos > i) {
                        commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(i, val + 1));
                    } else {
                        commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(pos, val + 1));
                    }
                }
            }
        }
        
        String[] leftPreTokensF = leftPre.split(",");
        String[] rightPreTokensF = rightPre.split(",");
        List<String> commonPath = new ArrayList<>();
        for (Map.Entry entry : commonPres.entrySet()) {
            AbstractMap.SimpleEntry e = (AbstractMap.SimpleEntry) entry.getValue();
            int pos = (Integer) e.getKey();
            int val = (Integer) e.getValue();
            System.out.println("Val : "+val);
            if ( val == leftPres.length ) {
                lcpIndex = pos;
                lcpProperty = leftPreTokensF[pos];
            }
        }
        
        System.out.println("L C P : "+lcpIndex + " : " + lcpProperty);
        
        for (String leftProp : leftPres) {
            //String[] leftPreTokens = rightProp.split(",");
            String[] mainPattern = leftProp.split(",");

            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(leftProp, lst);

            StringBuilder q = new StringBuilder();
            String prev_s = "";
            Set<String> set = new HashSet<>();
            Set<String> setFlat = new HashSet<>();

            System.out.println("Patterns " + patterns);

            for (String pattern : patterns) {
                String[] leftPreTokens = pattern.split(",");

                String simplified = "";
                if (leftPreTokens.length > mainPattern.length) {
                    simplified = "_" + StringUtils.substringAfter(leftPreTokens[leftPreTokens.length - 1], "#");
                    if (simplified.equals("")) {
                        simplified = "_" + StringUtils.substring(leftPreTokens[leftPreTokens.length - 1], StringUtils.lastIndexOf(leftPreTokens[leftPreTokens.length - 1], "/") + 1);
                    }
                }

                StringBuilder insq = new StringBuilder();
                insq.append("INSERT { GRAPH <" + tGraph + "> { ");

                prev_s = "?s";
                for (int i = 0; i < leftPreTokens.length - 2; i++) {
                    insq.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                insq.append(prev_s + " <" + domOnto + newPred + simplified + "> " + "?o" + (leftPreTokens.length - 1) + "");
                insq.append(" } } WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        insq.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        insq.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        insq.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        insq.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                insq.append("\n GRAPH <" + grConf.getMetadataGraphA() + "> {");
                prev_s = "?s";
                for (int i = 0; i < leftPreTokens.length - 1; i++) {
                    insq.append(prev_s + " <" + leftPreTokens[i] + "> " + "?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                insq.append(prev_s + " <" + leftPreTokens[leftPreTokens.length - 1] + "> " + "?o" + (leftPreTokens.length - 1) + " . ");

                insq.append("} }");

                System.out.println(insq);

                VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(insq.toString(), vSet);
                vur.exec();
            }
        }
    }
    
     /**
     * Function that handles metadata fusion
     * based on the selected action. A lot could be done
     * to optimize this single link version but
     * since this is relatively fast, the process is
     * implemented similarly to the batch fusion model for cinsistency
     *
     * @param action String represantation of the selected action
     * @param response servlet response
     * @throws SQLException if a servlet-specific error occurs
     * @throws UnsupportedEncodingException if an I/O error occurs
     */
    private void metadataKeepFlatRight(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {        
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lst = (List<String>)sess.getAttribute("property_patternsB");
        
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        // Find Least Common Prefix
        int lcpIndex = 0;
        String lcpProperty = "";
        Map<String, Map.Entry<Integer, Integer> > commonPres = new HashMap<>();
        for (String rightProp : rightPres) {
            String[] leftPreTokens = rightProp.split(",");
            String[] rightPreTokens = rightProp.split(",");

            // Find LCP (Least Common Prefix)
            for (int i = 0; i < rightPreTokens.length; i++) {
                if (!commonPres.containsKey(rightPreTokens[i])) {
                    commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(i, 1));
                } else {
                    Map.Entry retValue = commonPres.get(rightPreTokens[i]);
                    int pos = (Integer) retValue.getKey();
                    int val = (Integer) retValue.getValue();
                    if (pos > i) {
                        commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(i, val + 1));
                    } else {
                        commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(pos, val + 1));
                    }
                }
            }
        }
        
        String[] leftPreTokensF = leftPre.split(",");
        String[] rightPreTokensF = rightPre.split(",");
        List<String> commonPath = new ArrayList<>();
        for (Map.Entry entry : commonPres.entrySet()) {
            AbstractMap.SimpleEntry e = (AbstractMap.SimpleEntry) entry.getValue();
            int pos = (Integer) e.getKey();
            int val = (Integer) e.getValue();
            System.out.println("Val : "+val);
            if ( val == rightPres.length ) {
                lcpIndex = pos;
                lcpProperty = rightPreTokensF[pos];
            }
        }
        
        System.out.println("L C P : "+lcpIndex + " : " + lcpProperty);
        
        for (String rightProp : rightPres) {
            String[] leftPreTokens = rightProp.split(",");
            String[] mainPattern = rightProp.split(",");

            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(rightProp, lst);

            StringBuilder q = new StringBuilder();
            String prev_s = "";
            Set<String> set = new HashSet<>();
            Set<String> setFlat = new HashSet<>();

            System.out.println("Patterns " + patterns);

            for (String pattern : patterns) {
                set.clear();
                setFlat.clear();
                String[] rightPreTokens = pattern.split(",");

                String simplified = "";
                if (rightPreTokens.length > mainPattern.length) {
                    simplified = "_" + StringUtils.substringAfter(rightPreTokens[rightPreTokens.length - 1], "#");
                    if (simplified.equals("")) {
                        simplified = "_" + StringUtils.substring(rightPreTokens[rightPreTokens.length - 1], StringUtils.lastIndexOf(rightPreTokens[rightPreTokens.length - 1], "/") + 1);
                    }
                }
                StringBuilder insq = new StringBuilder();
                insq.append("INSERT { GRAPH <" + tGraph + "> { ");

                prev_s = "?s";
                for (int i = 0; i < mainPattern.length - 2; i++) {
                    insq.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                insq.append(prev_s + " <" + domOnto + newPred + simplified + "> " + "?o" + (rightPreTokens.length - 1) + "");
                insq.append(" } } WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        insq.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        insq.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        insq.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        insq.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                insq.append("\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
                //insq.append("} } WHERE {\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
                prev_s = "?s";
                for (int i = 0; i < rightPreTokens.length - 1; i++) {
                    insq.append(prev_s + " <" + rightPreTokens[i] + "> " + "?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                insq.append(prev_s + " <" + rightPreTokens[rightPreTokens.length - 1] + "> " + "?o" + (rightPreTokens.length - 1) + " . ");

                insq.append("} }");
                System.out.println(insq);

                VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(insq.toString(), vSet);
                vur.exec();
            }
        }
    }
     
     /**
     * Function that handles metadata fusion
     * based on the selected action. A lot could be done
     * to optimize this single link version but
     * since this is relatively fast, the process is
     * implemented similarly to the batch fusion model for cinsistency
     *
     * @param action String represantation of the selected action
     * @param response servlet response
     * @throws SQLException if a servlet-specific error occurs
     * @throws UnsupportedEncodingException if an I/O error occurs
     */
    private void metadataConcatenation(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lstA = (List<String>)sess.getAttribute("property_patternsA");
        List<String> lstB = (List<String>)sess.getAttribute("property_patternsB");

        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        // Find Least Common Prefix
        int lcpIndexB = 0;
        String lcpPropertyB = "";
        Map<String, Map.Entry<Integer, Integer> > commonPres = new HashMap<>();
        for (String rightProp : rightPres) {
            String[] leftPreTokens = rightProp.split(",");
            String[] rightPreTokens = rightProp.split(",");

            // Find LCP (Least Common Prefix)
            for (int i = 0; i < rightPreTokens.length; i++) {
                if (!commonPres.containsKey(rightPreTokens[i])) {
                    commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(i, 1));
                } else {
                    Map.Entry retValue = commonPres.get(rightPreTokens[i]);
                    int pos = (Integer) retValue.getKey();
                    int val = (Integer) retValue.getValue();
                    if (pos > i) {
                        commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(i, val + 1));
                    } else {
                        commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(pos, val + 1));
                    }
                }
            }
        }
        
        for (Map.Entry entry : commonPres.entrySet()) {
            String[] rightPreTokens = rightPre.split(",");
            
            AbstractMap.SimpleEntry e = (AbstractMap.SimpleEntry) entry.getValue();
            int pos = (Integer) e.getKey();
            int val = (Integer) e.getValue();
            System.out.println("Val : "+val);
            if ( val == rightPres.length ) {
                lcpIndexB = pos;
                lcpPropertyB = rightPreTokens[pos];
            }
        }
        
        System.out.println("L C P B : "+lcpIndexB + " : " + lcpPropertyB);
        
        int lcpIndexA = 0;
        String lcpPropertyA = "";
        commonPres = new HashMap<>();
        for (String leftProp : leftPres) {
            String[] leftPreTokens = leftProp.split(",");
            String[] rightPreTokens = leftProp.split(",");

            // Find LCP (Least Common Prefix)
            for (int i = 0; i < leftPreTokens.length; i++) {
                if (!commonPres.containsKey(leftPreTokens[i])) {
                    commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(i, 1));
                } else {
                    Map.Entry retValue = commonPres.get(leftPreTokens[i]);
                    int pos = (Integer) retValue.getKey();
                    int val = (Integer) retValue.getValue();
                    if (pos > i) {
                        commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(i, val + 1));
                    } else {
                        commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(pos, val + 1));
                    }
                }
            }
        }
        
        for (Map.Entry entry : commonPres.entrySet()) {
            String[] leftPreTokens = leftPre.split(",");
            
            AbstractMap.SimpleEntry e = (AbstractMap.SimpleEntry) entry.getValue();
            int pos = (Integer) e.getKey();
            int val = (Integer) e.getValue();
            System.out.println("Val : "+val);
            if ( val == leftPres.length ) {
                lcpIndexA = pos;
                lcpPropertyA = leftPreTokens[pos];
            }
        }
        
        System.out.println("L C P A : "+lcpIndexA + " : " + lcpPropertyA);
        
        int lcpIndex = lcpIndexA;
        String lcpProperty = lcpPropertyA;
        if ( lcpIndexA > lcpIndexB ) {
            lcpIndex = lcpIndexB;
            lcpProperty = lcpPropertyB;
        }
        
        final HashMap<String, StringBuilder> newObjs = Maps.newHashMap();
        for (String rightProp : rightPres) {
            //String[] leftPreTokens = leftPre.split(",");
            //String[] rightPreTokens = rightPre.split(",");
            String[] mainPattern = rightProp.split(",");
            
            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(rightProp, lstB);
            
            StringBuilder q = new StringBuilder();
            String prev_s = "";
            System.out.println("Patterns " + patterns);
            
            for (String pattern : patterns) {
                q.setLength(0);

                String[] rightPreTokens = pattern.split(",");
                
                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + rightPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                
                q.append("SPARQL SELECT ?s ?o" + (rightPreTokens.length - 1));
                prev_s = "?s";
                q.append(" WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                q.append("\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
                for (int i = 0; i < rightPreTokens.length; i++) {
                    q.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append("FILTER isLiteral("+prev_s+")} }");
                
                System.out.println(q.toString());
                PreparedStatement stmt;
                stmt = virt_conn.prepareStatement(q.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    //for (int i = 0; i < pres.length; i++) {
                    String o = rs.getString(2);
                    String s = rs.getString(1);

                    StringBuilder concat_str;
                    
                    concat_str = newObjs.get(s);
                    if (concat_str == null) {
                        concat_str = new StringBuilder();
                        newObjs.put(s, concat_str);
                    }
                    concat_str.append(o + " ");
                    
                    System.out.println("Subject " + s);
                    System.out.println("Object " + o);

                }
                System.out.println("Size " + newObjs.size());
            }
        }
        
        for (String leftProp : leftPres) {
            //String[] leftPreTokens = leftPre.split(",");
            //String[] rightPreTokens = rightPre.split(",");
            String[] mainPattern = leftProp.split(",");
            
            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(leftProp, lstA);
            
            StringBuilder q = new StringBuilder();
            String prev_s = "";
            System.out.println("Patterns " + patterns);
            for (String pattern : patterns) {
                q.setLength(0);
                String[] leftPreTokens = pattern.split(",");
                
                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + leftPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                
                q.append("SPARQL SELECT ?s ?o" + (leftPreTokens.length - 1));
                prev_s = "?s";
                q.append(" WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                q.append("\n GRAPH <" + grConf.getMetadataGraphA() + "> {");
                for (int i = 0; i < leftPreTokens.length; i++) {
                    q.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append("FILTER isLiteral("+prev_s+")} }");
                
                System.out.println(q.toString());
                PreparedStatement stmt;
                stmt = virt_conn.prepareStatement(q.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    //for (int i = 0; i < pres.length; i++) {
                    String o = rs.getString("o" + (leftPreTokens.length - 1));
                    String s = rs.getString(1);
                    
                    //System.out.println("The object is " + o);
                    StringBuilder concat_str;
                    
                    concat_str = newObjs.get(s);
                    if (concat_str == null) {
                        concat_str = new StringBuilder();
                        newObjs.put(s, concat_str);
                    }
                    concat_str.append(o + " ");

                    //System.out.println("Subject " + s);
                    //System.out.println("Object " + o);

                    String simplified = StringUtils.substringAfter(leftPreTokens[leftPreTokens.length - 1], "#");
                    if (simplified.equals("")) {
                        simplified = StringUtils.substring(leftPreTokens[leftPreTokens.length - 1], StringUtils.lastIndexOf(leftPreTokens[leftPreTokens.length - 1], "/") + 1);
                    }

                }
            }
        }
        
        String[] path = leftPres[0].split(",");
        StringBuilder q = new StringBuilder();
        System.out.println("Size " + newObjs.size());
        for (Map.Entry<String, StringBuilder> entry : newObjs.entrySet()) {
            String sub = entry.getKey();
            StringBuilder newObj = entry.getValue();
            newObj.setLength(newObj.length() - 1);
            q.setLength(0);
            q.append("INSERT { GRAPH <" + tGraph + "> { ");
            String prev_s = "<" + sub + ">";
            for (int i = 0; i < lcpIndex; i++) {
                q.append(prev_s + " <" + path[i] + "> ?o" + i + " . ");
                prev_s = "?o" + i;
            }
            q.append(prev_s + " <" + domOnto + newPred + "> \"" + newObj + "\" . ");
            q.append("} } WHERE {\n GRAPH <" + grConf.getMetadataGraphA() + "> {");
            prev_s = "<" + sub + ">";
            for (int i = 0; i < lcpIndex; i++) {
                q.append(prev_s + " <" + path[i] + "> ?o" + i + " . ");
                prev_s = "?o" + i;
            }
            q.append("} }");
            System.out.println("Last query " + q.toString());
            
            VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(q.toString(), vSet);
            vur.exec();
        }
    }
    
    private void metadataKeepConcatRight(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lst = (List<String>)sess.getAttribute("property_patternsB");
        
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        System.out.println("Short name : " + name + newPred );
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        // Find Least Common Prefix
        int lcpIndex = 0;
        String lcpProperty = "";
        Map<String, Map.Entry<Integer, Integer> > commonPres = new HashMap<>();
        for (String rightProp : rightPres) {
            String[] leftPreTokens = rightProp.split(",");
            String[] rightPreTokens = rightProp.split(",");

            // Find LCP (Least Common Prefix)
            for (int i = 0; i < rightPreTokens.length; i++) {
                if (!commonPres.containsKey(rightPreTokens[i])) {
                    commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(i, 1));
                } else {
                    Map.Entry retValue = commonPres.get(rightPreTokens[i]);
                    int pos = (Integer) retValue.getKey();
                    int val = (Integer) retValue.getValue();
                    if (pos > i) {
                        commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(i, val + 1));
                    } else {
                        commonPres.put(rightPreTokens[i], new AbstractMap.SimpleEntry<>(pos, val + 1));
                    }
                }
            }
        }
        
        for (Map.Entry entry : commonPres.entrySet()) {
            String[] leftPreTokens = leftPre.split(",");
            String[] rightPreTokens = rightPre.split(",");
            
            AbstractMap.SimpleEntry e = (AbstractMap.SimpleEntry) entry.getValue();
            int pos = (Integer) e.getKey();
            int val = (Integer) e.getValue();
            System.out.println("Val : "+val);
            if ( val == rightPres.length ) {
                lcpIndex = pos;
                lcpProperty = rightPreTokens[pos];
            }
        }
        
        System.out.println("L C P : "+lcpIndex + " : " + lcpProperty);
        
        final HashMap<String, StringBuilder> newObjs = Maps.newHashMap();
        for (String rightProp : rightPres) {
            //String[] leftPreTokens = leftPre.split(",");
            //String[] rightPreTokens = rightPre.split(",");
            String[] mainPattern = rightProp.split(",");
            
            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(rightProp, lst);
            
            StringBuilder q = new StringBuilder();
            String prev_s = "";
            System.out.println("Patterns " + patterns);
            
            for (String pattern : patterns) {
                q.setLength(0);

                String[] rightPreTokens = pattern.split(",");
                
                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + rightPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                
                q.append("SPARQL SELECT ?s ?o" + (rightPreTokens.length - 1));
                prev_s = "?s";
                q.append(" WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                q.append("\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
                for (int i = 0; i < rightPreTokens.length; i++) {
                    q.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append("FILTER isLiteral("+prev_s+")} }");
                
                System.out.println(q.toString());
                PreparedStatement stmt;
                stmt = virt_conn.prepareStatement(q.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    //for (int i = 0; i < pres.length; i++) {
                    String o = rs.getString(2);
                    String s = rs.getString(1);

                    StringBuilder concat_str;
                    
                    concat_str = newObjs.get(s);
                    if (concat_str == null) {
                        concat_str = new StringBuilder();
                        newObjs.put(s, concat_str);
                    }
                    concat_str.append(o + " ");
                    
                    System.out.println("Subject " + s);
                    System.out.println("Object " + o);

                }
                System.out.println("Size " + newObjs.size());
            }
        }
        
        String[] path = rightPres[0].split(",");
        for (Map.Entry<String, StringBuilder> entry : newObjs.entrySet()) {
            String sub = entry.getKey();
            StringBuilder newObj = entry.getValue();
            newObj.setLength(newObj.length() - 1);
            StringBuilder q = new StringBuilder();
            q.append("INSERT { GRAPH <" + tGraph + "> { ");
            String prev_s = "<" + sub + ">";
            for (int i = 0; i < lcpIndex; i++) {
                q.append(prev_s + " <" + path[i] + "> ?o" + i + " . ");
                prev_s = "?o" + i;
            }
            q.append(prev_s + " <" + domOnto + newPred + "> \"" + newObj + "\"");
            q.append("} } WHERE {\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
            prev_s = "<" + sub + ">";
            for (int i = 0; i < lcpIndex; i++) {
                q.append(prev_s + " <" + path[i] + "> ?o" + i + " . ");
                prev_s = "?o" + i;
            }
            q.append("} }");
            System.out.println("Last query " + q.toString());
            VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(q.toString(), vSet);
            vur.exec();
        }
    }
    
    private void metadataKeepConcatLeft(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws SQLException, UnsupportedEncodingException {
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lst = (List<String>)sess.getAttribute("property_patternsA");
        
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        // Find Least Common Prefix
        int lcpIndex = 0;
        String lcpProperty = "";
        Map<String, Map.Entry<Integer, Integer> > commonPres = new HashMap<>();
        for (String leftProp : leftPres) {
            String[] leftPreTokens = leftProp.split(",");

            // Find LCP (Least Common Prefix)
            for (int i = 0; i < leftPreTokens.length; i++) {
                if (!commonPres.containsKey(leftPreTokens[i])) {
                    commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(i, 1));
                } else {
                    Map.Entry retValue = commonPres.get(leftPreTokens[i]);
                    int pos = (Integer) retValue.getKey();
                    int val = (Integer) retValue.getValue();
                    if (pos > i) {
                        commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(i, val + 1));
                    } else {
                        commonPres.put(leftPreTokens[i], new AbstractMap.SimpleEntry<>(pos, val + 1));
                    }
                }
            }
        }
        
        for (Map.Entry entry : commonPres.entrySet()) {
            String[] leftPreTokens = leftPre.split(",");
            String[] rightPreTokens = rightPre.split(",");
            
            AbstractMap.SimpleEntry e = (AbstractMap.SimpleEntry) entry.getValue();
            int pos = (Integer) e.getKey();
            int val = (Integer) e.getValue();
            System.out.println("Val : "+val);
            if ( val == rightPres.length ) {
                lcpIndex = pos;
                lcpProperty = leftPreTokens[pos];
            }
        }
        
        System.out.println("L C P : "+lcpIndex + " : " + lcpProperty);
        
        System.out.println("Left Pres " + leftPres);
        final HashMap<String, StringBuilder> newObjs = Maps.newHashMap();
        for (String leftProp : leftPres) {
            //String[] leftPreTokens = leftPre.split(",");
            //String[] rightPreTokens = rightPre.split(",");
            String[] mainPattern = leftProp.split(",");
            
            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(leftProp, lst);
            
            StringBuilder q = new StringBuilder();
            String prev_s = "";
            System.out.println("Patterns " + patterns);
            for (String pattern : patterns) {
                q.setLength(0);
                String[] leftPreTokens = pattern.split(",");
                
                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + leftPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                
                q.append("SPARQL SELECT ?s ?o" + (leftPreTokens.length - 1));
                prev_s = "?s";
                q.append(" WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                q.append("\n GRAPH <" + grConf.getMetadataGraphA() + "> {");
                for (int i = 0; i < leftPreTokens.length; i++) {
                    q.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append("FILTER isLiteral("+prev_s+")");
                
                q.append("} }");
                System.out.println(q.toString());
                PreparedStatement stmt;
                stmt = virt_conn.prepareStatement(q.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    //for (int i = 0; i < pres.length; i++) {
                    String o = rs.getString("o" + (leftPreTokens.length - 1));
                    String s = rs.getString(1);
                    
                    System.out.println("The object is " + o);
                    StringBuilder concat_str;
                    
                    concat_str = newObjs.get(s);
                    if (concat_str == null) {
                        concat_str = new StringBuilder();
                        newObjs.put(s, concat_str);
                    }
                    concat_str.append(o + " ");

                    System.out.println("Subject " + s);
                    System.out.println("Object " + o);

                    String simplified = StringUtils.substringAfter(leftPreTokens[leftPreTokens.length - 1], "#");
                    if (simplified.equals("")) {
                        simplified = StringUtils.substring(leftPreTokens[leftPreTokens.length - 1], StringUtils.lastIndexOf(leftPreTokens[leftPreTokens.length - 1], "/") + 1);
                    }

                }
            }
        }
        
        String[] path = leftPres[0].split(",");
        for (Map.Entry<String, StringBuilder> entry : newObjs.entrySet()) {
            String sub = entry.getKey();
            StringBuilder newObj = entry.getValue();
            newObj.setLength(newObj.length() - 1);
            StringBuilder q = new StringBuilder();
            q.append("INSERT { GRAPH <" + tGraph + "> { ");
            String prev_s = "<" + sub + ">";
            for (int i = 0; i < lcpIndex; i++) {
                q.append(prev_s + " <" + path[i] + "> ?o" + i + " . ");
                prev_s = "?o" + i;
            }
            q.append(prev_s + " <" + domOnto + newPred + "> \"" + newObj + "\"");
            q.append("} } WHERE {\n GRAPH <" + grConf.getMetadataGraphA() + "> {");
            prev_s = "<" + sub + ">";
            for (int i = 0; i < lcpIndex; i++) {
                q.append(prev_s + " <" + path[i] + "> ?o" + i + " . ");
                prev_s = "?o" + i;
            }
            q.append("} }");
            System.out.println("Last query " + q.toString());
            VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(q.toString(), vSet);
            vur.exec();
        }
    }
    
    private void metadataKeepLeft(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws UnsupportedEncodingException {
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lst = (List<String>)sess.getAttribute("property_patternsA");
        
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[0];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        for (String leftProp : leftPres) {
            String[] mainPattern = leftProp.split(",");
                
            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(leftProp, lst);  
            
            for (String pattern : patterns) {
                String[] leftPreTokens = pattern.split(",");
                StringBuilder q = new StringBuilder();
                q.append("INSERT { GRAPH <" + tGraph + "> { ");
                String prev_s = "?s ";
                for (int i = 0; i < mainPattern.length - 1; i++) {
                    q.append(prev_s + " <" + mainPattern[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append(prev_s + " <" + domOnto + newPred + "> ?o" + (mainPattern.length - 1) + " . ");
                
                prev_s = "?o" + (mainPattern.length - 1);
                for (int i = mainPattern.length; i < leftPreTokens.length; i++) {
                    q.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                
                prev_s = "?s ";
                q.append("} } WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                q.append("\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
                for (int i = 0; i < leftPreTokens.length; i++) {
                    q.append(prev_s + " <" + leftPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append("} }");
                System.out.println(q.toString());
                VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(q.toString(), vSet);
                vur.exec();
            }
        }
    }
     
    private void metadataKeepRight(int idx, String tGraph, HttpSession sess, GraphConfig grConf, VirtGraph vSet, JSONBatchPropertyFusion[] selectedFusions, int activeCluster) throws UnsupportedEncodingException {
        Connection virt_conn = vSet.getConnection();
        String domOnto = "";
        List<String> lst = (List<String>)sess.getAttribute("property_patternsB");
        
        if ( grConf.isDominantA() ) 
            domOnto = (String)sess.getAttribute("domA");
        else 
            domOnto = (String)sess.getAttribute("domB");
        String name = URLDecoder.decode(selectedFusions[idx].pre, "UTF-8");
        //String longName = URLDecoder.decode(selectedFusions[idx].preL, "UTF-8");
        String longName = selectedFusions[idx].preL;
        name = StringUtils.replace(name, "&gt;", ">");
        longName = StringUtils.replace(longName, "&gt;", ">");
        String[] newPredTokes = name.split(Constants.PROPERTY_SEPARATOR);
        String newPred = "";
        if ( newPredTokes.length == 2 ) {
            newPred = newPredTokes[1];
        } else {
            newPred = newPredTokes[0];
        }
        newPred = newPred.replaceAll(",","_");
        System.out.println("Long name : "+longName);
        String[] predicates = longName.split(Constants.PROPERTY_SEPARATOR);
        String leftPre = predicates[0];
        String rightPre = predicates[1];
        leftPre = StringUtils.removeEnd(leftPre, "|");
        rightPre = StringUtils.removeEnd(rightPre, "|");
        String[] leftPres = leftPre.split("\\|");
        String[] rightPres = rightPre.split("\\|");
        System.out.println("Left pres "+leftPre);
        for ( String s : leftPres )
            System.out.print(s + " ");
        System.out.println();
        System.out.println("Right pres "+rightPre);   
        for ( String s : rightPres )
            System.out.print(s + " ");
        
        
        for (String rightProp : rightPres) {
            String[] mainPattern = rightProp.split(",");
                
            List<String> patterns = Utilities.findCommonPrefixedPropertyChains(rightProp, lst);
            
            for (String pattern : patterns) {
                String[] rightPreTokens = pattern.split(",");
                
                System.out.println("Pattern : " + pattern);
                System.out.println("Right Tokens : " + rightPreTokens.length);
                System.out.println("Main Pattern : " + mainPattern.length);
                
                StringBuilder q = new StringBuilder();
                q.append("INSERT { GRAPH <" + tGraph + "> { ");
                String prev_s = "?s ";
                for (int i = 0; i < mainPattern.length - 1; i++) {
                    q.append(prev_s + " <" + mainPattern[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append(prev_s + " <" + domOnto + newPred + "> ?o" + (mainPattern.length - 1) + " . ");
                
                prev_s = "?o" + (mainPattern.length - 1);
                for (int i = mainPattern.length; i < rightPreTokens.length; i++) {
                    q.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                
                prev_s = "?s ";
                q.append("} } WHERE {");
                if (activeCluster > -1) {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getClusterGraph()+"> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                } else {
                    if (grConf.isDominantA()) {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+  "> { ?s <http://www.w3.org/2002/07/owl#sameAs> ?o } . ");
                    } else {
                        q.append("GRAPH <"+ grConf.getLinksGraph()+ "> { ?o <http://www.w3.org/2002/07/owl#sameAs> ?s } . ");
                    }
                }
                q.append("\n GRAPH <" + grConf.getMetadataGraphB() + "> {");
                for (int i = 0; i < rightPreTokens.length; i++) {
                    q.append(prev_s + " <" + rightPreTokens[i] + "> ?o" + i + " . ");
                    prev_s = "?o" + i;
                }
                q.append("} }");
                System.out.println(q.toString());
                VirtuosoUpdateRequest vur = VirtuosoUpdateFactory.create(q.toString(), vSet);
                vur.exec();
            }
        }
    }
    
    private void offsetGeometriesA(String table, String linkTable, Float offx, Float offy, Connection dbConn) throws SQLException {
        // PGSQL Query
        // Join LINKS with GEOM table and offset the geometry
        // Result is stored inside FUSED GEOMETRIES table
        final String queryString = "INSERT INTO fused_geometries (subject_A, subject_B, geom) "
                + "SELECT "+linkTable+".nodea, "+linkTable+".nodeb, ST_Translate("+table+".geom, ?, ?) "
                + "FROM "+linkTable+" INNER JOIN "+table+" "
                + "ON ("+linkTable+".nodea = "+table+".subject)";
        
        System.out.println(queryString);
        PreparedStatement stmt = dbConn.prepareStatement(queryString);
        stmt.setFloat(1, offx);
        stmt.setFloat(2, offy);
        stmt.executeUpdate();
        
        stmt.close();
        
        dbConn.commit();
    }

    private void offsetGeometriesB(String table, String linkTable, Float offx, Float offy, Connection dbConn) throws SQLException {
        // PGSQL Query
        // Join LINKS with GEOM table and offset the geometry
        // Result is stored inside FUSED GEOMETRIES table
        final String queryString = "INSERT INTO fused_geometries (subject_A, subject_B, geom) "
                + "SELECT "+linkTable+".nodea, "+linkTable+".nodeb, ST_Translate("+table+".geom, ?, ?) "
                + "FROM "+linkTable+" INNER JOIN "+table+" "
                + "ON ("+linkTable+".nodeb = "+table+".subject)";
        
        PreparedStatement stmt = dbConn.prepareStatement(queryString);
        stmt.setFloat(1, offx);
        stmt.setFloat(2, offy);
        stmt.executeUpdate();
        
        stmt.close();
        
        dbConn.commit();
        
    }
    
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SQLException ex) {
            Logger.getLogger(BatchFusionServlet.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            
        }
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (SQLException ex) {
            Logger.getLogger(BatchFusionServlet.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}