/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.athenainnovation.imis.fusion.gis.json;

/**
 *
 * @author nick
 */
public class ClusteringResult {

    int cluster;

    public ClusteringResult(int cluster) {
        this.cluster = cluster;
    }

    public ClusteringResult() {
        this.cluster = -1;
    }

    public int getCluster() {
        return cluster;
    }

    public void setCluster(int cluster) {
        this.cluster = cluster;
    }
}
