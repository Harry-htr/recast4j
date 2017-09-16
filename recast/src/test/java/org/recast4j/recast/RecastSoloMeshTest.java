/*
Recast4J Copyright (c) 2015 Piotr Piastucki piotr@jtilia.org

This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.
Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:
1. The origin of this software must not be misrepresented; you must not
 claim that you wrote the original software. If you use this software
 in a product, an acknowledgment in the product documentation would be
 appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
 misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
*/
package org.recast4j.recast;

import static org.recast4j.recast.RecastConstants.RC_MESH_NULL_IDX;

import java.io.File;
import java.io.FileWriter;

import org.junit.Assert;
import org.junit.Test;
import org.recast4j.recast.RecastConstants.PartitionType;

public class RecastSoloMeshTest {

	private float m_cellSize = 0.3f;
	private float m_cellHeight = 0.2f;
	private float m_agentHeight = 2.0f;
	private float m_agentRadius = 0.6f;
	private float m_agentMaxClimb = 0.9f;
	private float m_agentMaxSlope = 45.0f;
	private int m_regionMinSize = 8;
	private int m_regionMergeSize = 20;
	private float m_edgeMaxLen = 12.0f;
	private float m_edgeMaxError = 1.3f;
	private int m_vertsPerPoly = 6;
	private float m_detailSampleDist = 6.0f;
	private float m_detailSampleMaxError = 1.0f;
	private PartitionType m_partitionType = PartitionType.WATERSHED;// 障碍物类型

	@Test
	public void testPerformance() {
		for (int i = 0; i < 10; i++) {
			testBuild("dungeon.obj", PartitionType.WATERSHED, 52, 16, 15, 223, 118, 118, 512, 289);
			testBuild("dungeon.obj", PartitionType.MONOTONE, 0, 17, 16, 210, 100, 100, 453, 264);
			testBuild("dungeon.obj", PartitionType.LAYERS, 0, 5, 5, 203, 97, 97, 447, 268);
		}
	}

	@Test
	public void testDungeonWatershed() {
		testBuild("dungeon.obj", PartitionType.WATERSHED, 52, 16, 15, 223, 118, 118, 512, 289);
	}

	@Test
	public void testDungeonMonotone() {
		testBuild("dungeon.obj", PartitionType.MONOTONE, 0, 17, 16, 210, 100, 100, 453, 264);
	}

	@Test
	public void testDungeonLayers() {
		testBuild("dungeon.obj", PartitionType.LAYERS, 0, 5, 5, 203, 97, 97, 447, 268);
	}

	@Test
	public void testWatershed() {
		testBuild("nav_test.obj", PartitionType.WATERSHED, 60, 48, 47, 349, 153, 153, 803, 560);
	}

	@Test
	public void testMonotone() {
		testBuild("nav_test.obj", PartitionType.MONOTONE, 0, 50, 49, 340, 185, 185, 873, 561);
	}

	@Test
	public void testLayers() {
		testBuild("nav_test.obj", PartitionType.LAYERS, 0, 19, 32, 312, 150, 150, 768, 529);
	}

	public void testBuild(String filename, PartitionType partitionType, int expDistance, int expRegions,
			int expContours, int expVerts, int expPolys, int expDetMeshes, int expDetVerts, int expDetTRis) {
		m_partitionType = partitionType;
		ObjImporter importer = new ObjImporter();
		InputGeom geom = importer.load(getClass().getResourceAsStream(filename));
		long time = System.nanoTime();
		float[] bmin = geom.getMeshBoundsMin();
		float[] bmax = geom.getMeshBoundsMax();
		float[] verts = geom.getVerts();
		int[] tris = geom.getTris();
		int ntris = tris.length / 3;
		//
		// Step 1. Initialize build config.
		//

		// Init build configuration from GUI
		RecastConfig cfg = new RecastConfig(partitionType, m_cellSize, m_cellHeight, m_agentHeight, m_agentRadius,
				m_agentMaxClimb, m_agentMaxSlope, m_regionMinSize, m_regionMergeSize, m_edgeMaxLen, m_edgeMaxError,
				m_vertsPerPoly, m_detailSampleDist, m_detailSampleMaxError, 0, SampleAreaModifications.SAMPLE_AREAMOD_GROUND);
		RecastBuilderConfig bcfg = new RecastBuilderConfig(cfg, bmin, bmax);
		Context m_ctx = new Context();
		//
		// Step 2. Rasterize input polygon soup. 栅格化输入的多边形
		//

		// Allocate voxel heightfield where we rasterize our input data to.
		Heightfield m_solid = new Heightfield(bcfg.width, bcfg.height, bcfg.bmin, bcfg.bmax, cfg.cs, cfg.ch);

		// Allocate array that can hold triangle area types.
		// If you have multiple meshes you need to process, allocate
		// and array which can hold the max number of triangles you need to
		// process.

		// Find triangles which are walkable based on their slope and rasterize
		// them.
		// If your input data is multiple meshes, you can transform them here,
		// calculate
		// the are type for each of the meshes and rasterize them.
		int[] m_triareas = Recast.markWalkableTriangles(m_ctx, cfg.walkableSlopeAngle, verts, tris, ntris, cfg.walkableAreaMod);
		RecastRasterization.rasterizeTriangles(m_ctx, verts, tris, m_triareas, ntris, m_solid, cfg.walkableClimb);
		//
		// Step 3. Filter walkables surfaces. 过滤可行走的面
		//

		// Once all geometry is rasterized, we do initial pass of filtering to
		// remove unwanted overhangs caused by the conservative rasterization
		// as well as filter spans where the character cannot possibly stand.
		RecastFilter.filterLowHangingWalkableObstacles(m_ctx, cfg.walkableClimb, m_solid);
		RecastFilter.filterLedgeSpans(m_ctx, cfg.walkableHeight, cfg.walkableClimb, m_solid);
		RecastFilter.filterWalkableLowHeightSpans(m_ctx, cfg.walkableHeight, m_solid);

		//
		// Step 4. Partition walkable surface to simple regions. 划分可行走的面  到简单的区域
		//

		// Compact the heightfield so that it is faster to handle from now on. 用heightfield创建Compactheightfield
		// This will result more cache coherent data as well as the neighbours
		// between walkable cells will be calculated.
        // 这将导致更多的缓存一致性数据以及步行单元之间的邻居将被计算。
		CompactHeightfield m_chf = Recast.buildCompactHeightfield(m_ctx, cfg.walkableHeight, cfg.walkableClimb,
				m_solid);

		// Erode the walkable area by agent radius.
		RecastArea.erodeWalkableArea(m_ctx, cfg.walkableRadius, m_chf);

		// (Optional) Mark areas.
		/*
		 * ConvexVolume vols = m_geom->getConvexVolumes(); for (int i = 0; i < m_geom->getConvexVolumeCount(); ++i)
		 * rcMarkConvexPolyArea(m_ctx, vols[i].verts, vols[i].nverts, vols[i].hmin, vols[i].hmax, (unsigned
		 * char)vols[i].area, *m_chf);
		 */

		// Partition the heightfield so that we can use simple algorithm later 分割高度场，以便稍后可以使用简单的算法对步行区进行三角测量
		// to triangulate the walkable areas.
		// There are 3 martitioning methods, each with some pros and cons: 有3种方法，每一种都有各自的利弊
		// 1) Watershed partitioning 流域划分
		// - the classic Recast partitioning 经典的Recast分区
		// - creates the nicest tessellation 创造最好的细分
		// - usually slowest 通常最慢
		// - partitions the heightfield into nice regions without holes or
		// overlaps 将高度区域划分为没有孔或重叠的好区域
		// - the are some corner cases where this method creates produces holes
		// and overlaps 这种方法创建的一些角落情况会产生孔和重叠
		// - holes may appear when a small obstacles is close to large open area 当小障碍物接近大开放区域时，可能会出现孔洞
		// (triangulation can handle this)三角测量可以处理这个
		// - overlaps may occur if you have narrow spiral corridors (i.e 如果您有狭窄的螺旋走廊可能会发生重叠
		// stairs), this make triangulation to fail 这使得三角测量失败
		// * generally the best choice if you precompute the nacmesh, use this
		// if you have large open areas 通常最好的选择，如果你预先计算网络使用这个，如果你有大的开放区域
		// 2) Monotone partioning 单调分区
		// - fastest 最快
		// - partitions the heightfield into regions without holes and overlaps 将高度区域划分为没有孔和重叠的区域
		// (guaranteed)
		// - creates long thin polygons, which sometimes causes paths with
		// detours 造成长薄的多边形，有时会导致路径弯曲
		// * use this if you want fast navmesh generation 如果你想要快速的navmesh生成，请使用它
		// 3) Layer partitoining 层划分
		// - quite fast 蛮快
		// - partitions the heighfield into non-overlapping regions 将高地划分为非重叠区域
		// - relies on the triangulation code to cope with holes (thus slower  依靠三角测量代码处理孔 （因此比单调分区慢）
		// than monotone partitioning)
		// - produces better triangles than monotone partitioning 比单调分割产生更好的三角形
		// - does not have the corner cases of watershed partitioning 没有流域划分的角落
		// - can be slow and create a bit ugly tessellation (still better than
		// monotone) 可能会缓慢，并创造一个有点丑陋的镶嵌（任然比单调划分好）
		// if you have large open areas with small obstacles (not a problem if
		// you use tiles) 如果你有大的开放区域有小的障碍（如果你使用瓷砖不是问题）
		// * good choice to use for tiled navmesh with medium and small sized
		// tiles 用于瓷砖和中小型瓷砖的好选择

		if (m_partitionType == PartitionType.WATERSHED) {
			// Prepare for region partitioning, by calculating distance field
			// along the walkable surface.
			RecastRegion.buildDistanceField(m_ctx, m_chf);
			// Partition the walkable surface into simple regions without holes.
			RecastRegion.buildRegions(m_ctx, m_chf, 0, cfg.minRegionArea, cfg.mergeRegionArea);
		} else if (m_partitionType == PartitionType.MONOTONE) {
			// Partition the walkable surface into simple regions without holes.
			// Monotone partitioning does not need distancefield.
			RecastRegion.buildRegionsMonotone(m_ctx, m_chf, 0, cfg.minRegionArea, cfg.mergeRegionArea);
		} else {
			// Partition the walkable surface into simple regions without holes.
			RecastRegion.buildLayerRegions(m_ctx, m_chf, 0, cfg.minRegionArea);
		}

		Assert.assertEquals("maxDistance", expDistance, m_chf.maxDistance);
		Assert.assertEquals("Regions", expRegions, m_chf.maxRegions);
		//
		// Step 5. Trace and simplify region contours.跟踪并简化区域轮廓
		//

		// Create contours.
		ContourSet m_cset = RecastContour.buildContours(m_ctx, m_chf, cfg.maxSimplificationError, cfg.maxEdgeLen,
				RecastConstants.RC_CONTOUR_TESS_WALL_EDGES);

		Assert.assertEquals("Contours", expContours, m_cset.conts.size());
		//
		// Step 6. Build polygons mesh from contours.从轮廓建立多边形网格。
		//

		// Build polygon navmesh from the contours.从轮廓建立多边形导航。
		PolyMesh m_pmesh = RecastMesh.buildPolyMesh(m_ctx, m_cset, cfg.maxVertsPerPoly);
		Assert.assertEquals("Mesh Verts", expVerts, m_pmesh.nverts);
		Assert.assertEquals("Mesh Polys", expPolys, m_pmesh.npolys);

		//
		// Step 7. Create detail mesh which allows to access approximate height
		// on each polygon.创建细节网格，允许访问每个多边形的近似高度。
		//

		PolyMeshDetail m_dmesh = RecastMeshDetail.buildPolyMeshDetail(m_ctx, m_pmesh, m_chf, cfg.detailSampleDist,
				cfg.detailSampleMaxError);
		Assert.assertEquals("Mesh Detail Meshes", expDetMeshes, m_dmesh.nmeshes);
		Assert.assertEquals("Mesh Detail Verts", expDetVerts, m_dmesh.nverts);
		Assert.assertEquals("Mesh Detail Tris", expDetTRis, m_dmesh.ntris);
		long time2 = System.nanoTime();
		System.out.println(filename + " : " + partitionType + "  " + (time2 - time) / 1000000 + " ms");
		saveObj(filename.substring(0, filename.lastIndexOf('.')) + "_" + partitionType + "_detail.obj", m_dmesh);
		saveObj(filename.substring(0, filename.lastIndexOf('.')) + "_" + partitionType + ".obj", m_pmesh);
	}

	private void saveObj(String filename, PolyMesh mesh) {
		try {
			File file = new File(filename);
			FileWriter fw = new FileWriter(file);
			for (int v = 0; v < mesh.nverts; v++) {
				fw.write("v " + (mesh.bmin[0] + mesh.verts[v * 3] * mesh.cs) + " "
						+ (mesh.bmin[1] + mesh.verts[v * 3 + 1] * mesh.ch) + " "
						+ (mesh.bmin[2] + mesh.verts[v * 3 + 2] * mesh.cs) + "\n");
			}

			for (int i = 0; i < mesh.npolys; i++) {
				int p = i * mesh.nvp * 2;
				fw.write("f ");
				for (int j = 0; j < mesh.nvp; ++j) {
					int v = mesh.polys[p + j];
					if (v == RC_MESH_NULL_IDX)
						break;
					fw.write((v + 1) + " ");
				}
				fw.write("\n");
			}
			fw.close();
		} catch (Exception e) {
		}
	}

	private void saveObj(String filename, PolyMeshDetail dmesh) {
		try {
			File file = new File(filename);
			FileWriter fw = new FileWriter(file);
			for (int v = 0; v < dmesh.nverts; v++) {
				fw.write(
						"v " + dmesh.verts[v * 3] + " " + dmesh.verts[v * 3 + 1] + " " + dmesh.verts[v * 3 + 2] + "\n");
			}

			for (int m = 0; m < dmesh.nmeshes; m++) {
				int vfirst = dmesh.meshes[m * 4];
				int tfirst = dmesh.meshes[m * 4 + 2];
				for (int f = 0; f < dmesh.meshes[m * 4 + 3]; f++) {
					fw.write("f " + (vfirst + dmesh.tris[(tfirst + f) * 4] + 1) + " "
							+ (vfirst + dmesh.tris[(tfirst + f) * 4 + 1] + 1) + " "
							+ (vfirst + dmesh.tris[(tfirst + f) * 4 + 2] + 1) + "\n");
				}
			}
			fw.close();
		} catch (Exception e) {
		}
	}
}
