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

import org.recast4j.recast.RecastConstants.PartitionType;

public class RecastConfig {
	public final PartitionType partitionType;

	/** The width/height size of tile's on the xz-plane. [Limit: >= 0] [Units: vx]
     * xz-plane 上tile的宽高
     * **/
	public final int tileSize;

	/** The xz-plane cell size to use for fields. [Limit: > 0] [Units: wu]
     * xz-plane 上一个单元格的宽高
     * **/
	public final float cs;

	/** The y-axis cell size to use for fields. [Limit: > 0] [Units: wu]
     * y-axis 上一个单元格的高度
     * **/
	public final float ch;

	/** The maximum slope that is considered walkable. [Limits: 0 <= value < 90] [Units: Degrees]
     * 可行走的最大坡度
     * **/
	public final float walkableSlopeAngle;

	/**
	 * Minimum floor to 'ceiling' height that will still allow the floor area to be considered walkable. [Limit: >= 3]
     * 可直接跨越的最大高度（高度不阻碍正常行走）
	 * [Units: vx]
	 **/
	public final int walkableHeight;

	/** Maximum ledge height that is considered to still be traversable. [Limit: >=0] [Units: vx]
     *可攀爬的最大高度
     * **/
	public final int walkableClimb;

	/**
	 * The distance to erode/shrink the walkable area of the heightfield away from obstructions. [Limit: >=0] [Units:vx]
     * 绕行障碍物半径
	 **/
	public final int walkableRadius;

	/** The maximum allowed length for contour edges along the border of the mesh. [Limit: >=0] [Units: vx]
	 * 被允许接近地图边缘的最大长度
     * **/
	public final int maxEdgeLen;

	/**
	 * The maximum distance a simplfied contour's border edges should deviate the original raw contour. [Limit: >=0]
     * 偏移原始轮廓的最大距离
	 * [Units: vx]
	 **/
	public final float maxSimplificationError;

	/** The minimum number of cells allowed to form isolated island areas. [Limit: >=0] [Units: vx]
     * 形成独立区域的最小单元格数量
     * **/
	public final int minRegionArea;

	/**
	 * Any regions with a span count smaller than this value will, if possible, be merged with larger regions. [Limit:>=0] [Units: vx]
     * 小于此区域的 区域将被合并
	 **/
	public final int mergeRegionArea;

	/**
	 * The maximum number of vertices allowed for polygons generated during the contour to polygon conversion process.
	 * [Limit: >= 3]
     * 多边形定点数的最大值
	 **/
	public final int maxVertsPerPoly;

	/**
	 * Sets the sampling distance to use when generating the detail mesh. (For height detail only.) [Limits: 0 or >=
	 * 0.9] [Units: wu]
     * 设置生成细节网格时使用的采样距离
	 **/
	public final float detailSampleDist;

	/**
	 * The maximum distance the detail mesh surface should deviate from heightfield data. (For height detail only.)
	 * [Limit: >=0] [Units: wu]
	 * 细节网格表面距离heightfield数据的最大距离
	 **/
	public final float detailSampleMaxError;

	public final AreaModification walkableAreaMod;

	public RecastConfig(PartitionType partitionType, float cellSize, float cellHeight, float agentHeight,
			float agentRadius, float agentMaxClimb, float agentMaxSlope, int regionMinSize, int regionMergeSize,
			float edgeMaxLen, float edgeMaxError, int vertsPerPoly, float detailSampleDist, float detailSampleMaxError,
			int tileSize, AreaModification walkableAreaMod) {
		this.partitionType = partitionType;
		this.cs = cellSize;
		this.ch = cellHeight;
		this.walkableSlopeAngle = agentMaxSlope;
		this.walkableHeight = (int) Math.ceil(agentHeight / ch); // 2/0.2 = 10
		this.walkableClimb = (int) Math.floor(agentMaxClimb / ch); // 0.9/0.2 = 4
		this.walkableRadius = (int) Math.ceil(agentRadius / cs); // 0.6/0.3 = 2
		this.maxEdgeLen = (int) (edgeMaxLen / cellSize); // 12/0.3 = 40
		this.maxSimplificationError = edgeMaxError;
		this.minRegionArea = regionMinSize * regionMinSize; // Note: area = size*size
		this.mergeRegionArea = regionMergeSize * regionMergeSize; // Note: area = size*size
		this.maxVertsPerPoly = vertsPerPoly;
		this.detailSampleDist = detailSampleDist < 0.9f ? 0 : cellSize * detailSampleDist;
		this.detailSampleMaxError = cellHeight * detailSampleMaxError;
		this.tileSize = tileSize;
		this.walkableAreaMod = walkableAreaMod;
	}

}
