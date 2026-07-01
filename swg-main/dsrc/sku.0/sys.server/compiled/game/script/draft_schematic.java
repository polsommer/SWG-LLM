/*
 Title:        draft_schematic
 Description:  Info about a draft schematic for use in a crafting session.
 NOTE: This class must remain binary-compatible with the native ms_clsDraftSchematic
       (exact field names/signatures). Do not rename/remove the m_* fields.
*/

package script;

import java.util.Hashtable;

public class draft_schematic
{
	// ===== IngredientType (legacy int constants; keep names/values) =====
	public static final int IT_none             = 0;
	public static final int IT_item             = 1;
	public static final int IT_template         = 2;
	public static final int IT_resourceType     = 3;
	public static final int IT_resourceClass    = 4;
	public static final int IT_templateGeneric  = 5;
	public static final int IT_schematic        = 6;
	public static final int IT_schematicGeneric = 7;

	// ===== Legacy nested types (field names/types unchanged) =====
	public static class simple_ingredient {
		public obj_id ingredient;  // id of ingredient
		public int    count;       // number of ingredients
		public obj_id source;      // who supplied the ingredient
		public int    xpType;      // xp type to grant the source
		public int    xpAmount;    // amount of xp to grant
		public simple_ingredient() {}
	}

	public static class slot {
		public string_id           name;            // slot name
		public int                 slotOption;      // slot option selected
		public int                 ingredientType;  // from IngredientType
		public String              ingredientName;  // resource class or template name
		public int                 amountRequired;  // needed to fill this slot
		public float               complexity;      // complexity adjustment
		public simple_ingredient[] ingredients;     // current ingredients
		public String              appearance;      // hardpoint/appearance string
		public slot() {}
	}

	public static class attribute {
		public string_id name;
		public float     minValue;
		public float     maxValue;           // absolute max
		public float     resourceMaxValue;   // <= maxValue (resource-limited)
		public float     currentValue;
		public int       scratch;            // dummy for computation

		public attribute() {}

		// Keep legacy semantics to avoid surprising old code:
		public int hashCode() { return name.getAsciiId().hashCode(); }
		public boolean equals(Object obj) { return obj == this; }
	}

	public static class custom {
		public String name;
		public int    value;
		public int    minValue;
		public int    maxValue;
		public custom() {}
	}

	// ===== CRITICAL: fields accessed by native code (names/signatures must match) =====
	// Keep them public so reflection or JNI GetFieldID both succeed.
	public int         m_category;                 // I
	public float       m_complexity;               // F
	public slot[]      m_slots       = null;       // [Lscript/draft_schematic$slot;
	public attribute[] m_attribs     = null;       // [Lscript/draft_schematic$attribute;
	public attribute[] m_experimentalAttribs = null;
	public custom[]    m_customizations = null;    // [Lscript/draft_schematic$custom;
	public Hashtable   m_attribMap   = null;       // java/util/Hashtable
	public int         m_objectTemplateCreated = 0; // I (template CRC)
	public String[]    m_scripts     = null;       // [Ljava/lang/String;

	/**
	 * IMPORTANT: native code constructs and fills this via JNI.
	 * Leave the no-arg ctor trivial.
	 */
	public draft_schematic() {}

	// ===== Read-only accessors kept for Java-side code =====
	public int getCategory() { return m_category; }
	public float getBaseComplexity() { return m_complexity; }
	public int getObjectTemplateCreated() { return m_objectTemplateCreated; }
	public String[] getScripts() { return m_scripts; }
	public slot[] getSlots() { return m_slots; }
	public attribute[] getAttribs() { return m_attribs; }
	public attribute[] getExperimentalAttribs() { return m_experimentalAttribs; }
	public custom[] getCustomizations() { return m_customizations; }
	public Hashtable getAttribMap() { return m_attribMap; }

	// Legacy map helpers (keep names/signatures so old scripts work)
	public attribute getExperimentalAttrib(attribute objectAttrib) {
		if (m_attribMap == null) return null;
		return (attribute) m_attribMap.get(objectAttrib);
	}
	public void setExperimentalAttrib(attribute objectAttrib, attribute experimentAttrib) {
		if (m_attribMap != null) m_attribMap.put(objectAttrib, experimentAttrib);
	}
	public attribute[] getObjectAttribs(attribute experimentalAttrib) {
		if (m_attribMap == null) return null;
		return (attribute[]) m_attribMap.get(experimentalAttrib);
	}

	// ===== Non-breaking quality-of-life helpers (don’t change field layout) =====

	/** Sum of amountRequired across all slots (null-safe). */
	public int getTotalRequiredCount() {
		int total = 0;
		if (m_slots != null) {
			for (slot s : m_slots) if (s != null) total += Math.max(0, s.amountRequired);
		}
		return total;
	}

	/** Effective complexity = base + sum(slot complexity contributions). */
	public float getEffectiveComplexity() {
		float c = m_complexity;
		if (m_slots != null) for (slot s : m_slots) if (s != null) c += s.complexity;
		return c;
	}

	/** Safe lookup by ascii id when legacy equals/hashCode causes Hashtable misses. */
	public attribute getExperimentalAttribSafe(attribute objectAttrib) {
		attribute direct = getExperimentalAttrib(objectAttrib);
		if (direct != null) return direct;
		if (m_attribMap == null || objectAttrib == null || objectAttrib.name == null) return null;
		String keyId = String.valueOf(objectAttrib.name.getAsciiId());
		for (Object k : m_attribMap.keySet()) {
			if (k instanceof attribute a && a.name != null) {
				if (keyId.equals(String.valueOf(a.name.getAsciiId())))
					return (attribute) m_attribMap.get(k);
			}
		}
		return null;
	}

	/** Safe reverse lookup (experimentation view) by ascii id. */
	public attribute[] getObjectAttribsSafe(attribute experimentalAttrib) {
		attribute[] direct = getObjectAttribs(experimentalAttrib);
		if (direct != null) return direct;
		if (m_attribMap == null || experimentalAttrib == null || experimentalAttrib.name == null) return null;
		String keyId = String.valueOf(experimentalAttrib.name.getAsciiId());
		for (Object k : m_attribMap.keySet()) {
			if (k instanceof attribute a && a.name != null) {
				if (keyId.equals(String.valueOf(a.name.getAsciiId())))
					return (attribute[]) m_attribMap.get(k);
			}
		}
		return null;
	}
}
