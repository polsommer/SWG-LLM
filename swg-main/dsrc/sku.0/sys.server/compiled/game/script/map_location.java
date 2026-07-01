/*
  Title:        map_location
  Description:  Wrapper for a map_location (binary-compatible upgrade)
*/

package script;

import java.util.Arrays;
import java.util.List;

public class map_location
{
	// ===== Legacy fields (do not rename or change types) =====
	protected obj_id m_locationId;
	protected String m_locationName;
	protected String m_category;
	protected String m_subCategory;
	protected long   m_x;
	protected long   m_y;
	protected byte   m_flags;

	// ===== Legacy constructors (unchanged) =====
	public map_location (long locationId, String locationName, String category, String subCategory, long x, long y, byte flags)
	{
		m_locationId   = (locationId == 0) ? null : obj_id.getObjId(locationId);
		m_locationName = locationName;
		m_category     = category;
		m_subCategory  = subCategory;
		m_x            = x;
		m_y            = y;
		m_flags        = flags;
	}

	public map_location (obj_id locationId, String locationName, String category, String subCategory, long x, long y, byte flags)
	{
		m_locationId   = locationId;
		m_locationName = locationName;
		m_category     = category;
		m_subCategory  = subCategory;
		m_x            = x;
		m_y            = y;
		m_flags        = flags;
	}

	// ===== Legacy getters (unchanged) =====
	public obj_id  getLocationId()   { return m_locationId; }
	public String  getLocationName() { return m_locationName; }
	public String  getCategory()     { return m_category; }
	public String  getSubCategory()  { return m_subCategory; }
	public long    getX()            { return m_x; }
	public long    getY()            { return m_y; }
	public byte    getFlags()        { return m_flags; }

	public boolean isInactive() { return (m_flags & base_class.MLF_INACTIVE) != 0; }
	public boolean isActive()   { return (m_flags & base_class.MLF_ACTIVE)   != 0; }

	// ===== Upgrades: non-breaking helpers =====

	/** Null-safe name/category getters (trimmed, never null). */
	public String getLocationNameSafe() { return m_locationName == null ? "" : m_locationName.trim(); }
	public String getCategorySafe()     { return m_category == null ? "" : m_category.trim(); }
	public String getSubCategorySafe()  { return m_subCategory == null ? "" : m_subCategory.trim(); }

	/** Coordinates as other types. */
	public int    getXi()  { return (int) m_x; }
	public int    getYi()  { return (int) m_y; }
	public double getXd()  { return (double) m_x; }
	public double getYd()  { return (double) m_y; }
	public long[] getXY()  { return new long[]{ m_x, m_y }; }

	/** Flag utilities. */
	public boolean hasFlag(int flag)              { return ((m_flags & flag) != 0); }
	public map_location withFlags(byte flags)     { return new map_location(m_locationId, m_locationName, m_category, m_subCategory, m_x, m_y, flags); }
	public map_location withFlag(int flag)        { return withFlags((byte)(m_flags |  flag)); }
	public map_location withoutFlag(int flag)     { return withFlags((byte)(m_flags & ~flag)); }
	public map_location setActive(boolean active) {
		byte f = m_flags;
		if (active) {
			f |=  base_class.MLF_ACTIVE;
			f &= ~base_class.MLF_INACTIVE;
		} else {
			f |=  base_class.MLF_INACTIVE;
			f &= ~base_class.MLF_ACTIVE;
		}
		return withFlags(f);
	}

	/** Copy/with-style helpers (immutability-friendly without changing field layout). */
	public map_location withLocationId(obj_id id)              { return new map_location(id, m_locationName, m_category, m_subCategory, m_x, m_y, m_flags); }
	public map_location withLocationId(long id)                { return new map_location(id, m_locationName, m_category, m_subCategory, m_x, m_y, m_flags); }
	public map_location withLocationName(String name)          { return new map_location(m_locationId, name, m_category, m_subCategory, m_x, m_y, m_flags); }
	public map_location withCategory(String category)          { return new map_location(m_locationId, m_locationName, category, m_subCategory, m_x, m_y, m_flags); }
	public map_location withSubCategory(String subCategory)    { return new map_location(m_locationId, m_locationName, m_category, subCategory, m_x, m_y, m_flags); }
	public map_location withX(long x)                          { return new map_location(m_locationId, m_locationName, m_category, m_subCategory, x, m_y, m_flags); }
	public map_location withY(long y)                          { return new map_location(m_locationId, m_locationName, m_category, m_subCategory, m_x, y, m_flags); }
	public map_location withXY(long x, long y)                 { return new map_location(m_locationId, m_locationName, m_category, m_subCategory, x, y, m_flags); }
	public map_location offsetBy(long dx, long dy)             { return new map_location(m_locationId, m_locationName, m_category, m_subCategory, m_x + dx, m_y + dy, m_flags); }

	/** Convenience factories. */
	public static map_location of(obj_id id, String name, String category, String subCategory, long x, long y, byte flags) {
		return new map_location(id, name, category, subCategory, x, y, flags);
	}
	public static map_location of(long id, String name, String category, String subCategory, long x, long y, byte flags) {
		return new map_location(id, name, category, subCategory, x, y, flags);
	}

	/** Human-readable debug string. */
	 public String toString() {
		String idStr = (m_locationId == null) ? "null" : String.valueOf(m_locationId.getValue());
		return "map_location{id=" + idStr +
				", name=\"" + getLocationNameSafe() + "\"" +
				", category=\"" + getCategorySafe() + "\"" +
				", subCategory=\"" + getSubCategorySafe() + "\"" +
				", x=" + m_x + ", y=" + m_y +
				", flags=" + String.format("0x%02X", m_flags) +
				(isActive() ? ",ACTIVE" : isInactive() ? ",INACTIVE" : "") +
				"}";
	}

	// ===== Optional: Datatable export hooks (pairs with DatatableWriter) =====
	public static List<String> tabHeader() {
		return Arrays.asList("locationId", "name", "category", "subCategory", "x", "y", "flags");
	}
	public static List<String> tabTypes() {
		// s,i,f,b not strictly needed here; sticking to strings/ints: id as string for safety
		return Arrays.asList("s","s","s","s","i","i","i");
	}
	public List<?> toTabRow() {
		String idStr = (m_locationId == null) ? "" : String.valueOf(m_locationId.getValue());
		return Arrays.asList(idStr, getLocationNameSafe(), getCategorySafe(), getSubCategorySafe(), (int)m_x, (int)m_y, (int)(m_flags & 0xFF));
	}
} // class map_location
