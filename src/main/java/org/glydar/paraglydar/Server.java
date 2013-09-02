package org.glydar.paraglydar;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.glydar.paraglydar.command.manager.CommandManager;
import org.glydar.paraglydar.event.manager.EventManager;
import org.glydar.paraglydar.models.Entity;
import org.glydar.paraglydar.models.Player;
import org.glydar.paraglydar.models.World;
import org.glydar.paraglydar.permissions.Permission;

public interface Server {

	public EventManager getEventManager();
	
	public CommandManager getCommandManager();

	public String getName();

	public String getVersion();

	public Collection<Player> getConnectedPlayers();

	public Collection<Entity> getConnectedEntities();

	public Entity getEntityByEntityID(long id);

	public Player getPlayerByEntityID(long id);
	
	public World getDefaultWorld();
	
	public Map<Long,World> getWorlds();

	public Logger getLogger();

	public boolean isRunning();

	public void shutdown();

	public void broadcastMessage(String message);

	public void broadcast(String message, String permission);

	public void broadcast(String message, Permission permission);
	
	public boolean isDebugging();
	
	public void debug(String message);
	
	public int getMaxPlayers();
	
	public boolean isPVPAllowed();

	public void setPVPAllowed(boolean allowPVP);
	
	public void setMaxPlayers(int maxPlayers);
	
	public int getPort();
	
}
