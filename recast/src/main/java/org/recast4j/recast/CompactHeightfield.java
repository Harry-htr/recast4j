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

/** A compact, static heightfield representing unobstructed space.
 * 一个紧凑的，静态的高度，代表无阻碍的空间
 * */
public class CompactHeightfield {

	/** The width of the heightfield. (Along the x-axis in cell units.)
     * 沿x-axis 轴 以cell为单位的宽
     * */
	public int width;
	/** The height of the heightfield. (Along the z-axis in cell units.)
     * 沿z轴 以cell为单位的长
     * */
	public int height;
	/** The number of spans in the heightfield.
     * span的数量 todo span 是what？
     * */
	public int spanCount;
	/** The walkable height used during the build of the field.  (See: RecastConfig::walkableHeight)
     * 在场地的建造过程中使用的可行走高度
     * */
	public int walkableHeight;
	/** The walkable climb used during the build of the field. (See: RecastConfig::walkableClimb)
     * 可攀爬的最大高度
     * */
	public int walkableClimb;
	/** The AABB border size used during the build of the field. (See: RecastConfig::borderSize) */
	public int borderSize;
	/** The maximum distance value of any span within the field.
     * span 之间最大间距
     * */
	public int maxDistance;
	/** The maximum region id of any span within the field.
     * span的最大区域id
     *
     * */
	public int maxRegions;
	/** The minimum bounds in world space. [(x, y, z)]
     * 在世界坐标系中的最小边界坐标
     * */
	public final float[] bmin = new float[3];
	/** The maximum bounds in world space. [(x, y, z)]
     * 世界坐标系中最大边界坐标
     * */
	public final float[] bmax = new float[3];
	/** The size of each cell. (On the xz-plane.)
     * 每一个cell的大小
     * */
	public float cs;
	/** The height of each cell. (The minimum increment along the y-axis.)
     * 每个cell的高度
     * */
	public float ch;
	/** Array of cells. [Size: #width*#height] */
	public CompactCell[] cells;
	/** Array of spans. [Size: #spanCount] */
	public CompactSpan[] spans;
	/** Array containing border distance data. [Size: #spanCount]
     * 边界距离数组
     * */
	public int[] dist;
	/** Array containing area id data. [Size: #spanCount]
     * 区域id数组
     * */
	public int[] areas;

}
