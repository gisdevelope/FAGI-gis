-- Insert
INSERT INTO fused_geometries (subject_A, subject_B, geom)
SELECT links.nodea, links.nodeb, CASE WHEN points_a >= points_b 
					THEN ST_Translate(a_g, b_x-a_x,b_y-a_y)
					ELSE ST_Translate(b_g, a_x-b_x,a_y-b_y)
				      END AS geom
FROM links 
INNER JOIN (SELECT dataset_a_geometries.subject AS a_s, 
		   dataset_b_geometries.subject AS b_s,
		  dataset_a_geometries.geom AS a_g, 
		  dataset_b_geometries.geom AS b_g,
		  ST_NPoints(dataset_a_geometries.geom) AS points_a,
		  ST_NPoints(dataset_b_geometries.geom) AS points_b,
		  ST_X(ST_Centroid(dataset_a_geometries.geom)) AS a_x,
		  ST_Y(ST_Centroid(dataset_a_geometries.geom)) AS a_y,
		  ST_X(ST_Centroid(dataset_b_geometries.geom)) AS b_x,
		  ST_Y(ST_Centroid(dataset_b_geometries.geom)) AS b_y
		FROM dataset_a_geometries, dataset_b_geometries) AS geoms 
		ON(links.nodea = geoms.a_s AND links.nodeb = geoms.b_s)

-- Verify
SELECT links.nodea, links.nodeb, CASE WHEN points_a >= points_b 
					THEN ST_asText(ST_Translate(a_g, b_x-a_x,b_y-a_y))
					ELSE ST_asText(ST_Translate(b_g, a_x-b_x,a_y-b_y))
				      END AS geom
FROM links 
INNER JOIN (SELECT dataset_a_geometries.subject AS a_s, 
		   dataset_b_geometries.subject AS b_s,
		  dataset_a_geometries.geom AS a_g, 
		  dataset_b_geometries.geom AS b_g,
		  ST_NPoints(dataset_a_geometries.geom) AS points_a,
		  ST_NPoints(dataset_b_geometries.geom) AS points_b,
		  ST_X(ST_Centroid(dataset_a_geometries.geom)) AS a_x,
		  ST_Y(ST_Centroid(dataset_a_geometries.geom)) AS a_y,
		  ST_X(ST_Centroid(dataset_b_geometries.geom)) AS b_x,
		  ST_Y(ST_Centroid(dataset_b_geometries.geom)) AS b_y
		FROM dataset_a_geometries, dataset_b_geometries) AS geoms 
		ON(links.nodea = geoms.a_s AND links.nodeb = geoms.b_s)