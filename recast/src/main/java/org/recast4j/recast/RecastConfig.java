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
		// 描述：> 0，设定体素(voxel)在X-Z-plane(OpenGL坐标系)上的大小,决定体素(voxel)在对原始几何图形进行采样时的精细度，值越小越精细，越大越粗略
        // 影响：较小的值能够产生更加精确接近原始集合图形的网格，减少在生成导航网格过程中生成多边形产生较大偏离度的问题(详见NavMesh的过程的Generate Detailed Mesh)，
        // 但是会消耗更加多的处理时间和内存占用,该设置为核心设置，会影响其他所有的参数设置
		this.cs = cellSize;

		// 描述：> 0，体素(voxel)在Y-plane(OpenGL坐标系)上的大小,决定体素(voxel)在对原始几何图形进行采样时的精细度，值越小越精细，越大越粗略,仅影响Y-Axis上的图形
		// 影响：较小的值能够产生更加精确接近原始集合图形的网格，减少在生成导航网格过程中生成多边形产生较大偏离度的问题
        // (详见NavMesh的过程的Generate Detailed Mesh，但是与cellSize不一样，较低的值并不会对内存消耗产生有显著影响)，有一点需要注意，
        // 较低的值虽然能够使网格贴合原始图形，但是如果是凹凸不平的地形，较低的值可能会造成邻接的网格之间产生断裂，导致本来应该连在一起的网格造成分离
        this.ch = cellHeight;
		// 描述：> 0,最大可通过的斜坡的倾斜度
        // 影响：过低的值会导致无法通过原本可通过的地形
		this.walkableSlopeAngle = agentMaxSlope;

		// 描述：> 0, 最低可通过高度，设定从底部边界到顶部边界之间的最低高度，该高度为模型可通过的高度
        // 影响：会影响场景中一些地形或者组件的可通过范围，比如桌子，如果设定值过低，会导致模型高度就算高过桌子也会从桌子中间传过去，设
        // 置过高会导致部分不被准许通过的场景也能穿过，另外一点，minTraversableHeight的设定值的大小至少得是cellHeight的2倍
		this.walkableHeight = (int) Math.ceil(agentHeight / ch); // 2/0.2 = 10 可直接行走的最大高度

        // 描述：> 0,可跨越不同地形时的高度，设定像是从普通平面移动到楼梯这样的地形是否可通过的高度阀值
        // 影响：过低的值可能会导致无法通过原本可通过的地形，比如从平面到楼梯，导航网格生成时可能会发现楼梯的导航网格断裂缺失
        // 导致无法楼梯寻路，设定值过高会导致原本不该通过的小物件能够被角色跨越,同样，值设定必须大于cellHeight的2倍
		this.walkableClimb = (int) Math.floor(agentMaxClimb / ch); // 0.9/0.2 = 4.5

        // 描述：>= 0,可行走区域与阻挡物之前的距离大小
        // 影响：值设定必须大于cellSize的2倍才能产生效果(因为导航网格的生成的实际上是建立在由voxel所组成的三维世界中)，
        // 当开启clipLedges时，实际的border会比较大
		this.walkableRadius = (int) Math.ceil(agentRadius / cs); // 0.6/0.3 = 2  可行走区域与障碍物之间的距离

        // 描述：>= 0, 指示网格边界的多边形最大的边
        // 影响：当设置该值>0时，会在长的多边形边界上增加顶点，同时生产新的边，这样有助于减少相当数量的削长的三角形，当该值set to 0时，会关闭该特性
        this.maxEdgeLen = (int) (edgeMaxLen / cellSize); // 12/0.3 = 40 边界是没有邻接或邻接了障碍物的轮廓，在为区域生成轮廓（为后面创建多边形准备）为了得到最优的三角形

        // 描述：>= 0, 网格边界与原始集合图形的偏离量
        // 影响：值越小，生成的网格三角形越多越密，也越贴近原始几何图形,同样也会消耗更多的资源来做这些工作
        this.maxSimplificationError = edgeMaxError;


        // 描述：> 0, 最小的无法被连接的区域（这里的区域指的是在网格生成之前，某些要与其他区域连接的区域）
        // 影响：当一个区域小于设定的这个minUnconnectedRegionSize，在生成网格时不会被考虑进去，也就是说这些区域上面不会生成网格
        // 举个例子：在某些场景中，一些物件是不能被寻路的，比如固定场景中的一些岩石等等，当minUnconnectedRegionSize的值设置恰当时，
        // 在岩石等这些不能被寻路的物件上面不会生成网格，这样寻路时就不会走这里，当minUnconnectedRegionSize的值设置的过分小时，
        // 可能让岩石也生成了一些孤立的小网格（在官方资料中被称为island，
        // 就像一些孤岛一样，这里无法被寻路，但是确也生成了网格, 如果游戏中准许actor通过瞬移的方式进入孤岛，那么这里生产的nav网络就是有意义的）
		this.minRegionArea = regionMinSize * regionMinSize; // Note: area = size*size

        // 描述：> 0, 合并区域尺寸，当一个区域小于该尺寸时，如果可以，则会被合并进一些大的区域
        // 影响：合理设置值可以规避一些区域产生算法生出一些不必要的小三角形的问题，当该值过小时，会产生一些又细又长的三角形，产生一些很
        // 细小的区域，需要把这个值设定到一个合理的大小
        //
		this.mergeRegionArea = regionMergeSize * regionMergeSize; // Note: area = size*size

        // 描述：>= 3, 每个多边形的最大顶点数
        // 影响：越高的值意味着越复杂的多边形，也意味着越高的性能消耗，通常情况下6个顶点能够平衡需求和性能
		this.maxVertsPerPoly = vertsPerPoly;


		// 在这最后的阶段，凸多边形网格会被Delaunay三角化算法三角化，于是可以增加高度的细节。
        // 在多边形的内部或者边缘添加顶点，来确保网格与原始几何体表面等价。(DetailSampleDistance和DetailMaxDeviation)
        // 注意：从技术上讲，这一步是可缺省的。细节网格在寻路中不是必须的，但是存在细节网格的情况下
        // 某些查询会返回更加精确的数据。

        //描述：>= 0, 设置采样距离，类似游戏中的凹凸贴图类似概念，用于NavMesh过程中的Generate Detailed Mesh,匹配原始集合图形表面的网格（利用生成更加精细的三角形保证网格来贴合那些凹凸不平的地表）
        //影响：越高的值意味着越贴近原始几何图形表面的最终网格，也意味着越高的性能消耗，当值在0.9以下时会关闭这个特性
        //当该特性关闭时,可以看到多边形的边缘一直沿着原始几何图形的表面，但是中央区域缺没有网格
		this.detailSampleDist = detailSampleDist < 0.9f ? 0 : cellSize * detailSampleDist;

        //描述：>= 0, 设置最大的采样偏移距离，最好和contourSampleDistance结合起来看，其效果的精确度受到contourSampleDistance的影响
        //影响：当contourSampleDistance为0时，这个特性选项无效,在实际使用时发现在这个值越接近0，其生成的网格就越偏离原始图形
		this.detailSampleMaxError = cellHeight * detailSampleMaxError;

		this.tileSize = tileSize;
		this.walkableAreaMod = walkableAreaMod;
	}

}
