/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.athenainnovation.imis.fagi.gis.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import static com.hp.hpl.jena.enhanced.BuiltinPersonalities.model;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryException;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import gr.athenainnovation.imis.fusion.gis.gui.workers.DBConfig;
import gr.athenainnovation.imis.fusion.gis.gui.workers.GraphConfig;
import gr.athenainnovation.imis.fusion.gis.json.JSONFusedGeometries;
import gr.athenainnovation.imis.fusion.gis.json.JSONFusedGeometry;
import gr.athenainnovation.imis.fusion.gis.json.JSONRequestResult;
import gr.athenainnovation.imis.fusion.gis.utils.Constants;
import gr.athenainnovation.imis.fusion.gis.utils.Log;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoQueryExecution;
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;

/**
 *
 * @author Nick Vitsas
 */
@WebServlet(name = "ScanGeometriesServlet", urlPatterns = {"/ScanGeometriesServlet"})
public class ScanGeometriesServlet extends HttpServlet {

    private static final org.apache.log4j.Logger LOG = Log.getClassFAGILogger(ScanGeometriesServlet.class);    
    
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
            throws ServletException {
        response.setContentType("text/html;charset=UTF-8");
        
        HttpSession                 sess;
        GraphConfig                 grConf;
        JSONFusedGeometries         ret;
        JSONRequestResult           res;
        
        try (PrintWriter out = response.getWriter()) {
            ObjectMapper mapper = new ObjectMapper();
            //mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

            ret = new JSONFusedGeometries();
            res = new JSONRequestResult();
            ret.setResult(res);
            
            sess = request.getSession(false);
            
            if ( sess == null ) {
                res.setMessage("Failed to create session!");
                res.setStatusCode(-1);
                
                out.println(mapper.writeValueAsString(ret));

                out.close();
                
                return;
            }
            
            grConf = (GraphConfig) sess.getAttribute("gr_conf");

            final String restrictionWGS = "?s ?p1 _:a . _:a <" + Constants.AS_WKT_REGEX + "> ?g";
            final String geoQuery = "SELECT ?s ?g WHERE { " + restrictionWGS + " }";

            HttpAuthenticator authenticator = new SimpleAuthenticator("dba", "dba".toCharArray());
            try (QueryEngineHTTP qeh = new QueryEngineHTTP(grConf.getEndpointT(), geoQuery, authenticator)) {
                
                // [TODO] Make this more generic
                String reqType = "";
                for (String s : QueryEngineHTTP.supportedSelectContentTypes) {
                    if (s.contains("xml")) {
                        reqType = s;
                    }
                }
                
                qeh.setSelectContentType(reqType);
                qeh.addDefaultGraph((String) sess.getAttribute("t_graph"));
                final com.hp.hpl.jena.query.ResultSet resultSet = qeh.execSelect();

                while (resultSet.hasNext()) {
                    final QuerySolution querySolution = resultSet.next();
                    //final String predicate = querySolution.getResource("?p").getURI();
                    RDFNode s, p1, g, p2;
                    s = querySolution.get("?s");
                    g = querySolution.get("?g");

                    String geo = g.asLiteral().getString();
                    int ind = geo.indexOf("^^");
                    if (ind > 0) {
                        geo = geo.substring(0, ind);
                    }
                    
                    String sub = s.toString();
                    ret.getGeoms().add(new JSONFusedGeometry(geo, sub));
                }
            } catch (QueryException ex) {
                LOG.trace("Scan Geometries Query failed");
                LOG.debug("Scan Geometries Query failed");
            }
            
            if (ret.getGeoms().size() > 0) {
                res.setMessage("Datasets accepted!(Found fused geometries)");
                res.setStatusCode(0);
            } else {
                res.setMessage("Datasets accepted!(Found NO fused geometries)");
                res.setStatusCode(1);
            }

            out.println(mapper.writeValueAsString(ret));
        } catch (IOException ex) {
            LOG.trace("Could not open Servlet Writer stream");
            LOG.debug("Could not open Servlet Writer stream");
            
            throw new ServletException("Servlet Error");
        }
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
        processRequest(request, response);
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
        processRequest(request, response);
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
