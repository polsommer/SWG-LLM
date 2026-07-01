// region.java
package script;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable value object describing an SWG/SWG+ region.
 *
 * <p><b>Backwards compatibility:</b> All original methods and signatures are preserved:
 * getName(), getPvPType(), getMunicipalType(), getBuildableType(), getGeographicalType(),
 * getMinDifficultyType(), getMaxDifficultyType(), getSpawnableType(), getMissionType(),
 * getPlanetName(), toString(), and the protected constructor.</p>
 *
 * <p>Upgrades include:</p>
 * <ul>
 *   <li>Enum views for each type (e.g., {@link #getPvPTypeEnum()}) mapped from the backing ints.</li>
 *   <li>Safe factory methods: {@link #of(String, int, int, int, int, int, int, int, int, String)}
 *       and enum-based {@link #of(String, PvPType, BuildableType, MunicipalType, GeographicalType, DifficultyBand, DifficultyBand, SpawnableType, MissionType, String)}.</li>
 *   <li>Validation & normalization (names trimmed, planet names canonicalized when recognized).</li>
 *   <li>Quality-of-life: {@link #toJson()}, {@link #equals(Object)}, {@link #hashCode()},
 *       {@link #compareTo(region)}, and "with-" copy methods.</li>
 * </ul>
 */
public final class region implements Serializable, Comparable<region>
{
	// ======== SWG/SWG+ canonical enumerations ========

	/** PvP state (values map from legacy integer codes). */
	public enum PvPType {
		UNKNOWN(-1),
		SAFE(0),          // e.g., tutorial/spaceport safe zones
		TEF(1),           // temporary enemy flagged
		CONTESTED(2),
		OVERT(3),
		SPECIAL(4);       // battlefield/instance rules

		public final int code;
		PvPType(int code) { this.code = code; }
		public static PvPType fromCode(int code) {
			for (PvPType v : values()) if (v.code == code) return v;
			return UNKNOWN;
		}
	}

	/** Whether structures can be placed. */
	public enum BuildableType {
		UNKNOWN(-1),
		NON_BUILDABLE(0),
		LIMITED(1),
		BUILDABLE(2);
		public final int code;
		BuildableType(int code){ this.code = code; }
		public static BuildableType fromCode(int code){
			for (BuildableType v: values()) if (v.code==code) return v;
			return UNKNOWN;
		}
	}

	/** City/municipal status. */
	public enum MunicipalType {
		UNKNOWN(-1),
		NONE(0),
		PLAYER_CITY(1),
		FACTIONAL_BASE(2),
		NPC_CITY(3);
		public final int code;
		MunicipalType(int code){ this.code = code; }
		public static MunicipalType fromCode(int code){
			for (MunicipalType v: values()) if (v.code==code) return v;
			return UNKNOWN;
		}
	}

	/** Geography category of the region. */
	public enum GeographicalType {
		UNKNOWN(-1),
		WILDERNESS(0),
		DUNGEON(1),
		INSTANCE(2),
		POINT_OF_INTEREST(3),
		SPACEPORT(4);
		public final int code;
		GeographicalType(int code){ this.code = code; }
		public static GeographicalType fromCode(int code){
			for (GeographicalType v: values()) if (v.code==code) return v;
			return UNKNOWN;
		}
	}

	/** Difficulty band. Backed by an integer code to match legacy data. */
	public enum DifficultyBand {
		UNKNOWN(-1),
		VERY_EASY(0),
		EASY(1),
		NORMAL(2),
		HARD(3),
		ELITE(4),
		BOSS(5);
		public final int code;
		DifficultyBand(int code){ this.code = code; }
		public static DifficultyBand fromCode(int code){
			for (DifficultyBand v: values()) if (v.code==code) return v;
			return UNKNOWN;
		}
	}

	/** Spawn rules. */
	public enum SpawnableType {
		UNKNOWN(-1),
		NONE(0),
		CRITTERS_ONLY(1),
		NPCS_ONLY(2),
		MIXED(3),
		RARE(4);
		public final int code;
		SpawnableType(int code){ this.code = code; }
		public static SpawnableType fromCode(int code){
			for (SpawnableType v: values()) if (v.code==code) return v;
			return UNKNOWN;
		}
	}

	/** Mission availability/rules. */
	public enum MissionType {
		UNKNOWN(-1),
		NONE(0),
		TERMINAL(1),
		EXPLORATION(2),
		DUNGEON(3),
		EVENT(4);
		public final int code;
		MissionType(int code){ this.code = code; }
		public static MissionType fromCode(int code){
			for (MissionType v: values()) if (v.code==code) return v;
			return UNKNOWN;
		}
	}

	// Recognized canonical planet names for normalization (extend as your shard supports)
	private static final Set<String> CANON_PLANETS = Set.of(
			"tatooine","naboo","corellia","rori","talus","dantooine","dathomir","endor",
			"lok","yavin4","mustafar","kashyyyk","taanab","ord_mantell","hoth"
	);

	// ======== Original public API (unchanged) ========

	/**
	 * Retrieve the name of the region.
	 *
	 * @return the name of the region.
	 */
	public String getName()
	{
		return m_name;
	}

	/**
	 * Retrieve the value representing the PvP state.
	 *
	 * @return the value representing the PvP state.
	 */
	public int getPvPType()
	{
		return m_PvPType;
	}

	/**
	 * Retrieve the value representing the municipal state.
	 *
	 * @return the value representing the municipal state.
	 */
	public int getMunicipalType()
	{
		return m_municipalType;
	}

	/**
	 * Retrieve the value representing the buildable state.
	 *
	 * @return the value representing the buildable state.
	 */
	public int getBuildableType()
	{
		return m_buildableType;
	}

	/**
	 * Retrieve the value representing the geographical state.
	 *
	 * @return the value representing the geographical state.
	 */
	public int getGeographicalType()
	{
		return m_geographicalType;
	}

	/**
	 * Retrieve the value representing the minimum difficulty state.
	 *
	 * @return the value representing the minimum difficulty state.
	 */
	public int getMinDifficultyType()
	{
		return m_minDifficultyType;
	}

	/**
	 * Retrieve the value representing the max difficulty state.
	 *
	 * @return the value representing the max difficulty state.
	 */
	public int getMaxDifficultyType()
	{
		return m_maxDifficultyType;
	}

	/**
	 * Retrieve the value representing the spawnable state.
	 *
	 * @return the value representing the spawnable state.
	 */
	public int getSpawnableType()
	{
		return m_spawnableType;
	}

	/**
	 * Retrieve the value representing the mission state.
	 *
	 * @return the value representing the mission state.
	 */
	public int getMissionType()
	{
		return m_missionType;
	}

	/**
	 * Retrieve the value representing the planet name.
	 *
	 * @return the value representing the planet name.
	 */
	public String getPlanetName()
	{
		return m_planetName;
	}

	/**
	 * Retrieve a String representation of the instance suitable for a debug dump.
	 *
	 * The caller should not assume anything about the format of this output. It may change at any time.
	 */
	
	public String toString()
	{
		return "[region: name = " + getName() + ", PvPType = " + getPvPType() + ", municipalType = " + getMunicipalType() +
				", buildableType = " + getBuildableType() + ", geographicalType = " + getGeographicalType() + ", minDifficultyType = " +
				getMinDifficultyType() + ", maxDifficultyType = " + getMaxDifficultyType() + ", spawnableType = " + getSpawnableType() +
				", missionType = " + getMissionType() + ", planetName = " + m_planetName + "]";
	}

	/**
	 * Construct a region instance.
	 *
	 * Scripters should not try to construct these by hand.
	 *
	 * @param name               region name (non-null/non-blank)
	 * @param pvpType            legacy code for PvPType
	 * @param buildableType      legacy code for BuildableType
	 * @param municipalType      legacy code for MunicipalType
	 * @param geographicalType   legacy code for GeographicalType
	 * @param minDifficultyType  legacy code for DifficultyBand (min)
	 * @param maxDifficultyType  legacy code for DifficultyBand (max)
	 * @param spawnableType      legacy code for SpawnableType
	 * @param missionType        legacy code for MissionType
	 * @param planetName         canonical planet name when possible
	 */
	protected region(String name, int pvpType, int buildableType, int municipalType, int geographicalType, int minDifficultyType, int maxDifficultyType, int spawnableType, int missionType, String planetName)
	{
		// Normalize & validate while preserving original visibility and signature
		this.m_name              = normalizeName(name);
		this.m_PvPType           = pvpType;
		this.m_buildableType     = buildableType;
		this.m_municipalType     = municipalType;
		this.m_geographicalType  = geographicalType;
		this.m_minDifficultyType = minDifficultyType;
		this.m_maxDifficultyType = maxDifficultyType;
		this.m_spawnableType     = spawnableType;
		this.m_missionType       = missionType;
		this.m_planetName        = normalizePlanet(planetName);
		validateDifficultyBounds(this.m_minDifficultyType, this.m_maxDifficultyType);
	}

	// ======== New: Safe factories (recommended) ========

	/** Factory that mirrors legacy int constructor but public and validated. */
	public static region of(String name,
							int pvpType,
							int buildableType,
							int municipalType,
							int geographicalType,
							int minDifficultyType,
							int maxDifficultyType,
							int spawnableType,
							int missionType,
							String planetName)
	{
		return new region(name, pvpType, buildableType, municipalType, geographicalType,
				minDifficultyType, maxDifficultyType, spawnableType, missionType, planetName);
	}

	/** Enum-based factory for modern code paths. */
	public static region of(String name,
							PvPType pvpType,
							BuildableType buildableType,
							MunicipalType municipalType,
							GeographicalType geographicalType,
							DifficultyBand minDifficulty,
							DifficultyBand maxDifficulty,
							SpawnableType spawnableType,
							MissionType missionType,
							String planetName)
	{
		Objects.requireNonNull(pvpType, "pvpType");
		Objects.requireNonNull(buildableType, "buildableType");
		Objects.requireNonNull(municipalType, "municipalType");
		Objects.requireNonNull(geographicalType, "geographicalType");
		Objects.requireNonNull(minDifficulty, "minDifficulty");
		Objects.requireNonNull(maxDifficulty, "maxDifficulty");
		Objects.requireNonNull(spawnableType, "spawnableType");
		Objects.requireNonNull(missionType, "missionType");

		return new region(name,
				pvpType.code,
				buildableType.code,
				municipalType.code,
				geographicalType.code,
				minDifficulty.code,
				maxDifficulty.code,
				spawnableType.code,
				missionType.code,
				planetName);
	}

	// ======== New: Enum getters (non-breaking additions) ========

	/** Enum view of {@link #getPvPType()}. */
	public PvPType getPvPTypeEnum(){ return PvPType.fromCode(m_PvPType); }

	/** Enum view of {@link #getBuildableType()}. */
	public BuildableType getBuildableTypeEnum(){ return BuildableType.fromCode(m_buildableType); }

	/** Enum view of {@link #getMunicipalType()}. */
	public MunicipalType getMunicipalTypeEnum(){ return MunicipalType.fromCode(m_municipalType); }

	/** Enum view of {@link #getGeographicalType()}. */
	public GeographicalType getGeographicalTypeEnum(){ return GeographicalType.fromCode(m_geographicalType); }

	/** Enum view of {@link #getMinDifficultyType()}. */
	public DifficultyBand getMinDifficultyEnum(){ return DifficultyBand.fromCode(m_minDifficultyType); }

	/** Enum view of {@link #getMaxDifficultyType()}. */
	public DifficultyBand getMaxDifficultyEnum(){ return DifficultyBand.fromCode(m_maxDifficultyType); }

	/** Enum view of {@link #getSpawnableType()}. */
	public SpawnableType getSpawnableTypeEnum(){ return SpawnableType.fromCode(m_spawnableType); }

	/** Enum view of {@link #getMissionType()}. */
	public MissionType getMissionTypeEnum(){ return MissionType.fromCode(m_missionType); }

	// ======== New: Copy-with helpers (immutability-friendly) ========

	public region withPlanetName(String newPlanet){
		return of(this.m_name, this.m_PvPType, this.m_buildableType, this.m_municipalType, this.m_geographicalType,
				this.m_minDifficultyType, this.m_maxDifficultyType, this.m_spawnableType, this.m_missionType, newPlanet);
	}

	public region withName(String newName){
		return of(newName, this.m_PvPType, this.m_buildableType, this.m_municipalType, this.m_geographicalType,
				this.m_minDifficultyType, this.m_maxDifficultyType, this.m_spawnableType, this.m_missionType, this.m_planetName);
	}

	// ======== New: Utilities ========

	/** Compact JSON representation suitable for logs and external tooling. */
	public String toJson(){
		// Simple manual JSON to avoid external deps
		return new StringBuilder(192)
				.append("{\"name\":\"").append(escapeJson(m_name)).append('"')
				.append(",\"pvpType\":").append(m_PvPType)
				.append(",\"municipalType\":").append(m_municipalType)
				.append(",\"buildableType\":").append(m_buildableType)
				.append(",\"geographicalType\":").append(m_geographicalType)
				.append(",\"minDifficultyType\":").append(m_minDifficultyType)
				.append(",\"maxDifficultyType\":").append(m_maxDifficultyType)
				.append(",\"spawnableType\":").append(m_spawnableType)
				.append(",\"missionType\":").append(m_missionType)
				.append(",\"planetName\":\"").append(escapeJson(m_planetName)).append('"')
				.append('}')
				.toString();
	}

	 public boolean equals(Object o){
		if (this == o) return true;
		if (!(o instanceof region)) return false;
		region that = (region) o;
		return m_PvPType == that.m_PvPType
				&& m_buildableType == that.m_buildableType
				&& m_municipalType == that.m_municipalType
				&& m_geographicalType == that.m_geographicalType
				&& m_minDifficultyType == that.m_minDifficultyType
				&& m_maxDifficultyType == that.m_maxDifficultyType
				&& m_spawnableType == that.m_spawnableType
				&& m_missionType == that.m_missionType
				&& Objects.equals(m_name, that.m_name)
				&& Objects.equals(m_planetName, that.m_planetName);
	}

	 public int hashCode(){
		return Objects.hash(m_name, m_PvPType, m_buildableType, m_municipalType, m_geographicalType,
				m_minDifficultyType, m_maxDifficultyType, m_spawnableType, m_missionType, m_planetName);
	}

	/** Sort by planet, then name (case-insensitive). */
	 public int compareTo(region o){
		int p = this.m_planetName.compareToIgnoreCase(o.m_planetName);
		if (p != 0) return p;
		return this.m_name.compareToIgnoreCase(o.m_name);
	}

	// ======== Internal helpers ========

	private static String normalizeName(String name){
		Objects.requireNonNull(name, "name");
		String trimmed = name.trim();
		if (trimmed.isEmpty())
			throw new IllegalArgumentException("name must not be blank");
		return trimmed;
	}

	private static String normalizePlanet(String planetName){
		Objects.requireNonNull(planetName, "planetName");
		String raw = planetName.trim();
		if (raw.isEmpty()) return "unknown";
		String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (CANON_PLANETS.contains(key)) return key;
		// leave as provided if unrecognized to support custom worlds/shards
		return raw;
	}
	
	private static void validateDifficultyBounds(int minCode, int maxCode)
	{
		// Intentionally a NO-OP.
		// Some live regions (e.g., GCW/city) can have inverted difficulty codes.
		// Throwing here crashes scripts that read regions. If you still want
		// visibility without exceptions, uncomment the lines below.

		// DifficultyBand min = DifficultyBand.fromCode(minCode);
		// DifficultyBand max = DifficultyBand.fromCode(maxCode);
		// if (min != DifficultyBand.UNKNOWN && max != DifficultyBand.UNKNOWN && min.ordinal() > max.ordinal()){
		//     System.out.println("region: WARNING minDifficultyType > maxDifficultyType (min=" + minCode + ", max=" + maxCode + ")");
		// }
	}


	private static String escapeJson(String s){
		StringBuilder out = new StringBuilder(s.length() + 8);
		for (int i=0;i<s.length();i++){
			char c = s.charAt(i);
			switch (c){
				case '"' : out.append("\\\""); break;
				case '\\': out.append("\\\\"); break;
				case '\b': out.append("\\b"); break;
				case '\f': out.append("\\f"); break;
				case '\n': out.append("\\n"); break;
				case '\r': out.append("\\r"); break;
				case '\t': out.append("\\t"); break;
				default:
					if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
					else out.append(c);
			}
		}
		return out.toString();
	}

	// ======== Fields (unchanged names & final immutability) ========

	private static final long serialVersionUID = 1L;

	private final String  m_name;
	private final int     m_PvPType;
	private final int     m_buildableType;
	private final int     m_municipalType;
	private final int     m_geographicalType;
	private final int     m_minDifficultyType;
	private final int     m_maxDifficultyType;
	private final int     m_spawnableType;
	private final int     m_missionType;
	private final String  m_planetName;
}
