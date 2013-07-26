package org.glydar.glydar;

import org.glydar.glydar.models.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;

public class Server implements Runnable {

	private final Logger LOGGER = Logger.getLogger(Glydar.class.getName());

    private boolean running = true;

    public final boolean DEBUG;

    public Server(boolean debug) {
        this.DEBUG = debug;
    }

    public Collection<Player> getConnectedPlayers() {
        return Player.getConnectedPlayers();
    }

	public Logger getLogger() {
		return LOGGER;
	}

	public boolean isRunning() {
		return running;
	}

    @Override
    public void run() {
        while (running) {
            /* TODO Server loop / tick code.
               Eventually; All periodic events will be processed here, such as AI logic, etc for entities.
             */
        }
    }
}
