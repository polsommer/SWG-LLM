package script.space.structure;

import script.dictionary;
import script.library.create;
import script.location;
import script.obj_id;

public class guild_space_station_interior extends script.base_script
{
    public guild_space_station_interior()
    {
    }

    public int OnInitialize(obj_id self) throws InterruptedException
    {
        ensureTerminals(self);
        return SCRIPT_CONTINUE;
    }

    public int OnUnloadedFromMemory(obj_id self) throws InterruptedException
    {
        return SCRIPT_CONTINUE;
    }

    public int msgEnsureTerminals(obj_id self, dictionary params) throws InterruptedException
    {
        ensureTerminals(self);
        return SCRIPT_CONTINUE;
    }

    private void ensureTerminals(obj_id building) throws InterruptedException
    {
        spawnTerminal(building, "guild.station.space_terminal", "object/tangible/terminal/terminal_space.iff", 2.5f, 0.0f, 7.5f, 90.0f);
        spawnTerminal(building, "guild.station.travel_terminal", "object/tangible/terminal/terminal_travel.iff", -2.5f, 0.0f, 7.5f, -90.0f);
    }

    private void spawnTerminal(obj_id building, String objvar, String template, float x, float y, float z, float yaw) throws InterruptedException
    {
        if (hasObjVar(building, objvar))
        {
            obj_id existing = getObjIdObjVar(building, objvar);
            if (isIdValid(existing))
            {
                return;
            }
            removeObjVar(building, objvar);
        }
        obj_id cell = getCellId(building, "entry");
        if (!isIdValid(cell))
        {
            obj_id[] cells = getCellIds(building);
            if (cells != null && cells.length > 0)
            {
                cell = cells[0];
            }
        }
        location spawnLoc = getLocation(building);
        spawnLoc.x = x;
        spawnLoc.y = y;
        spawnLoc.z = z;
        spawnLoc.cell = cell;
        obj_id terminal = create.object(template, spawnLoc);
        if (!isIdValid(terminal))
        {
            dictionary params = new dictionary();
            messageTo(building, "msgEnsureTerminals", params, 10.0f, false);
            return;
        }
        setYaw(terminal, yaw);
        setObjVar(building, objvar, terminal);
    }
}
