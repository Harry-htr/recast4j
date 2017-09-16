/*
Copyright (c) 2009-2010 Mikko Mononen memon@inside.org
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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.recast4j.recast.ChunkyTriMesh.ChunkyTriMeshNode;
import org.recast4j.recast.RecastConstants.PartitionType;

public class RecastBuilder {

	public class RecastBuilderResult {
		private final PolyMesh pmesh;
		private final PolyMeshDetail dmesh;

		public RecastBuilderResult(PolyMesh pmesh, PolyMeshDetail dmesh) {
			this.pmesh = pmesh;
			this.dmesh = dmesh;
		}

		public PolyMesh getMesh() {
			return pmesh;
		}

		public PolyMeshDetail getMeshDetail() {
			return dmesh;
		}
	}

	public RecastBuilderResult[][] buildTiles(InputGeom geom, RecastConfig cfg, int threads) {
		float[] bmin = geom.getMeshBoundsMin();
		float[] bmax = geom.getMeshBoundsMax();
		int[] twh = Recast.calcTileCount(bmin, bmax, cfg.cs, cfg.tileSize);
		int tw = twh[0];
		int th = twh[1];
		RecastBuilderResult[][] result = null;
		if (threads == 1) {
			result = buildSingleThread(geom, cfg, bmin, bmax, tw, th);
		} else {
			result = buildMultiThread(geom, cfg, bmin, bmax, tw, th, threads);
		}
		return result;
	}

	private RecastBuilderResult[][] buildSingleThread(InputGeom geom, RecastConfig cfg, float[] bmin, float[] bmax, int tw, int th) {
		RecastBuilderResult[][] result = new RecastBuilderResult[tw][th];
		for (int x = 0; x < tw; ++x) {
			for (int y = 0; y < th; ++y) {
				result[x][y] = build(geom, new RecastBuilderConfig(cfg, bmin, bmax, x, y, true));
			}
		}
		return result;
	}

	private RecastBuilderResult[][] buildMultiThread(InputGeom geom, RecastConfig cfg, float[] bmin, float[] bmax, int tw, int th, int threads) {
		ExecutorService ec = Executors.newFixedThreadPool(threads);
		RecastBuilderResult[][] result = new RecastBuilderResult[tw][th];
		for (int x = 0; x < tw; ++x) {
			for (int y = 0; y < th; ++y) {
				final int tx = x;
				final int ty = y;
				ec.submit((Runnable) () -> {
					result[tx][ty] = build(geom, new RecastBuilderConfig(cfg, bmin, bmax, tx, ty, true));
				});
			}
		}
		ec.shutdown();
		try {
			ec.awaitTermination(1000, TimeUnit.HOURS);
		} catch (InterruptedException e) {
		}
		return result;
	}
	
	public RecastBuilderResult build(InputGeom geom, RecastBuilderConfig bcfg) {

		RecastConfig cfg = bcfg.cfg;
		Context ctx = new Context();
		CompactHeightfield chf = buildCompactHeightfield(geom, bcfg, ctx);

		// Partition the heightfield so that we can use simple algorithm later
		// to triangulate the walkable areas.分隔高度场，以便我们可以使用简单的算法来对可步行区域进行三角测量
		// There are 3 martitioning methods, each with some pros and cons:有3种方法，每种都有一些利弊：
		// 1) Watershed partitioning流域划分
		// - the classic Recast partitioning经典的Recast分区
		// - creates the nicest tessellation创造最好的细分
		// - usually slowest通常最慢
		// - partitions the heightfield into nice regions without holes or
		// overlaps将高度区域划分为没有孔或重叠的好区域
		// - the are some corner cases where this method creates produces holes
		// and overlaps 这种方法创建的一些角落情况会产生孔和重叠
		// - holes may appear when a small obstacles is close to large open area当小障碍物接近大开放区域时，可能会出现孔洞
		// (triangulation can handle this)三角测量可以处理这个
		// - overlaps may occur if you have narrow spiral corridors (i.e
		// stairs), this make triangulation to fail如果您有狭窄的螺旋走廊可能会发生重叠这使得三角测量失败
		// * generally the best choice if you precompute the nacmesh, use this
		// if you have large open areas 一般来说，如果您预先计算网络，最好的选择，如果您有大的开放区域，请使用此选项
		// 2) Monotone partioning 单调分区
		// - fastest 最快
		// - partitions the heightfield into regions without holes and overlaps 将高度区域划分为没有孔和重叠的区域
		// (guaranteed)保证
		// - creates long thin polygons, which sometimes causes paths with
		// detours 造成长薄的多边形，有时会导致路径弯曲
		// * use this if you want fast navmesh generation 如果你想要快速的navmesh生成，请使用它
		// 3) Layer partitoining 层划分
		// - quite fast 蛮快
		// - partitions the heighfield into non-overlapping regions 将高地划分为非重叠区域
		// - relies on the triangulation code to cope with holes (thus slower
		// than monotone partitioning)依靠三角测量代码处理孔（因此比单调分割慢）
		// - produces better triangles than monotone partitioning 比单调分割产生更好的三角形
		// - does not have the corner cases of watershed partitioning 没有流域划分的角落
		// - can be slow and create a bit ugly tessellation (still better than
		// monotone)可能会缓慢，并创造一个有点丑陋的镶嵌（还好比单调）
		// if you have large open areas with small obstacles (not a problem if
		// you use tiles)如果你有大的开放区域有小障碍（如果你使用瓷砖不是问题）
		// * good choice to use for tiled navmesh with medium and small sized
		// tiles用于瓷砖和中小型瓷砖的好选择

		if (cfg.partitionType == PartitionType.WATERSHED) {
			// Prepare for region partitioning, by calculating distance field
			// along the walkable surface.
			RecastRegion.buildDistanceField(ctx, chf);
			// Partition the walkable surface into simple regions without holes.
			RecastRegion.buildRegions(ctx, chf, bcfg.borderSize, cfg.minRegionArea, cfg.mergeRegionArea);
		} else if (cfg.partitionType == PartitionType.MONOTONE) {
			// Partition the walkable surface into simple regions without holes.
			// Monotone partitioning does not need distancefield.
			RecastRegion.buildRegionsMonotone(ctx, chf, bcfg.borderSize, cfg.minRegionArea, cfg.mergeRegionArea);
		} else {
			// Partition the walkable surface into simple regions without holes.
			RecastRegion.buildLayerRegions(ctx, chf, bcfg.borderSize, cfg.minRegionArea);
		}

		//
		// Step 5. Trace and simplify region contours.
		//

		// Create contours.
		ContourSet cset = RecastContour.buildContours(ctx, chf, cfg.maxSimplificationError, cfg.maxEdgeLen,
				RecastConstants.RC_CONTOUR_TESS_WALL_EDGES);

		//
		// Step 6. Build polygons mesh from contours.
		//

		PolyMesh pmesh = RecastMesh.buildPolyMesh(ctx, cset, cfg.maxVertsPerPoly);

		//
		// Step 7. Create detail mesh which allows to access approximate height
		// on each polygon.
		//

		PolyMeshDetail dmesh = RecastMeshDetail.buildPolyMeshDetail(ctx, pmesh, chf, cfg.detailSampleDist,
				cfg.detailSampleMaxError);
		return new RecastBuilderResult(pmesh, dmesh);
	}

	private CompactHeightfield buildCompactHeightfield(InputGeom geom, RecastBuilderConfig bcfg, Context ctx) {
		RecastConfig cfg = bcfg.cfg;
		//
		// Step 2. Rasterize input polygon soup.
		//

		// Allocate voxel heightfield where we rasterize our input data to.分配voxel heightfield，我们将输入数据进行栅格化
		Heightfield solid = new Heightfield(bcfg.width, bcfg.height, bcfg.bmin, bcfg.bmax, cfg.cs, cfg.ch);

		// Allocate array that can hold triangle area types.分配可以容纳三角形区域类型的数组
		// If you have multiple meshes you need to process, allocate  and array which can hold the max number of triangles you need to process.如果你有多个网格，你需要处理，分配
		// 而数组可以容纳你需要处理的最大三角形数

		// Find triangles which are walkable based on their slope and rasterize them.找到可以在斜坡上行走的三角形，并将其栅格化
		// If your input data is multiple meshes, you can transform them here,如果你的输入数据是多个网格，你可以在这里进行转换，
		// calculate
		// the are type for each of the meshes and rasterize them.每个网格都是类型，并对它们进行栅格化
		float[] verts = geom.getVerts();
		boolean tiled = cfg.tileSize > 0;
		int totaltris = 0;
		if (tiled) {
			ChunkyTriMesh chunkyMesh = geom.getChunkyMesh();
			float[] tbmin = new float[2];
			float[] tbmax = new float[2];
			tbmin[0] = bcfg.bmin[0];
			tbmin[1] = bcfg.bmin[2];
			tbmax[0] = bcfg.bmax[0];
			tbmax[1] = bcfg.bmax[2];
			List<ChunkyTriMeshNode> nodes = chunkyMesh.getChunksOverlappingRect(tbmin, tbmax);
			for (ChunkyTriMeshNode node : nodes) {
				int[] tris = node.tris;
				int ntris = tris.length / 3;
				totaltris += ntris;
				int[] m_triareas = Recast.markWalkableTriangles(ctx, cfg.walkableSlopeAngle, verts, tris, ntris, cfg.walkableAreaMod);
				RecastRasterization.rasterizeTriangles(ctx, verts, tris, m_triareas, ntris, solid, cfg.walkableClimb);
			}
		} else {
			int[] tris = geom.getTris();
			int ntris = tris.length / 3; // 三角形个数
			int[] m_triareas = Recast.markWalkableTriangles(ctx, cfg.walkableSlopeAngle, verts, tris, ntris, cfg.walkableAreaMod);
			totaltris = ntris;
			RecastRasterization.rasterizeTriangles(ctx, verts, tris, m_triareas, ntris, solid, cfg.walkableClimb);
		}
		//
		// Step 3. Filter walkables surfaces.
		//

		// Once all geometry is rasterized, we do initial pass of filtering to
		// remove unwanted overhangs caused by the conservative rasterization
		// as well as filter spans where the character cannot possibly stand.
		RecastFilter.filterLowHangingWalkableObstacles(ctx, cfg.walkableClimb, solid);
		RecastFilter.filterLedgeSpans(ctx, cfg.walkableHeight, cfg.walkableClimb, solid);
		RecastFilter.filterWalkableLowHeightSpans(ctx, cfg.walkableHeight, solid);

		//
		// Step 4. Partition walkable surface to simple regions.
		//

		// Compact the heightfield so that it is faster to handle from now on.
		// This will result more cache coherent data as well as the neighbours
		// between walkable cells will be calculated.
		CompactHeightfield chf = Recast.buildCompactHeightfield(ctx, cfg.walkableHeight, cfg.walkableClimb, solid);

		// Erode the walkable area by agent radius.
		RecastArea.erodeWalkableArea(ctx, cfg.walkableRadius, chf);
		// (Optional) Mark areas.
		for (ConvexVolume vol : geom.getConvexVolumes()) {
			RecastArea.markConvexPolyArea(ctx, vol.verts, vol.hmin, vol.hmax, vol.areaMod, chf);
		}
		return chf;
	}

	public HeightfieldLayerSet buildLayers(InputGeom geom, RecastBuilderConfig cfg) {
		Context ctx = new Context();
		CompactHeightfield chf = buildCompactHeightfield(geom, cfg, ctx);
		return RecastLayers.buildHeightfieldLayers(ctx, chf, cfg.borderSize, cfg.cfg.walkableHeight);
	}

}
