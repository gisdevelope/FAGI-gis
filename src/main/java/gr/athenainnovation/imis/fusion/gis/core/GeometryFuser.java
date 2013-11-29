package gr.athenainnovation.imis.fusion.gis.core;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import gr.athenainnovation.imis.fusion.gis.transformations.AbstractFusionTransformation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.log4j.Logger;

/**
 * Provides methods for obtaining RDF links, scoring and then applying fusion transformations against them.
 * @author Thomas Maroulis
 */
public class GeometryFuser {
    private static final Logger LOG = Logger.getLogger(GeometryFuser.class);
    
    private static final String DB_URL = "jdbc:postgresql:";
    
    private Connection connection;
    
    /**
     * Apply given fusion transformation on list of links.
     * @param transformation fusion transformation
     * @param links list of links
     * @throws SQLException
     */
    public void fuse(final AbstractFusionTransformation transformation, final List<Link> links) throws SQLException {
        for(Link link : links) {
            transformation.fuse(connection, link.getNodeA(), link.getNodeB());
        }
    }
    
    /**
     * Score given fusion transformation for each link in list.
     * @param transformation fusion transformation
     * @param links list of links
     * @return map with score results (value) for each link (key)
     * @throws SQLException
     */
    public Map<String, Double> score(final AbstractFusionTransformation transformation, final List<Link> links) throws SQLException {
        Map<String, Double> scores = new HashMap<>();
        
        for(Link link : links) {
            scores.put(link.getKey(), transformation.score(connection, link.getNodeA(), link.getNodeB()));
        }
        
        return scores;
    }
    
    /**
     * Parses given RDF link file.
     * @param linksFile link file
     * @return list of links
     * @throws ParseException if link file contains invalid links
     */
    public static List<Link> parseLinksFile(final String linksFile) throws ParseException {
        List<Link> output = new ArrayList<>();
        
        final Model model = RDFDataMgr.loadModel(linksFile);
        
        final StmtIterator iter = model.listStatements();
        
        while(iter.hasNext()) {
            final Statement statement = iter.nextStatement();
            final String nodeA = statement.getSubject().getURI();
            final String nodeB;
            final RDFNode object = statement.getObject();
            if(object.isResource()) {
                nodeB = object.asResource().getURI();
            }
            else {
                throw new ParseException("Failed to parse link (object not a resource): " + statement.toString(), 0);
            }
            
            output.add(new Link(nodeA, nodeB));
        }
        
        return output;
    }
    
    /**
     * Connect to the database
     * @param dbConfig database configuration
     * @throws SQLException 
     */
    public void connect(final DBConfig dbConfig) throws SQLException {
        final String url = DB_URL.concat(dbConfig.getDBName());
        connection = DriverManager.getConnection(url, dbConfig.getDBUsername(), dbConfig.getDBPassword());
        connection.setAutoCommit(false);
        LOG.info("Connection to db established.");
    }
    
    /**
     * Clean-up. Close held resources.
     */
    public void clean() {
        try {
            if(connection != null) {
                connection.close();
            }
            
            LOG.info("Database connection closed.");
        }
        catch (SQLException ex) {
            LOG.warn(ex.getMessage(), ex);
        }
    }
}