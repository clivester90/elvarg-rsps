package com.elvarg.game.content.minigames.impl.pestcontrol;

import com.elvarg.game.content.minigames.Minigame;
import com.elvarg.game.entity.impl.npc.NPC;
import com.elvarg.game.entity.impl.object.GameObject;
import com.elvarg.game.entity.impl.player.Player;
import com.elvarg.game.model.Location;
import com.elvarg.game.model.areas.Area;
import com.elvarg.game.model.areas.impl.pestcontrol.PestControlArea;
import com.elvarg.game.model.areas.impl.pestcontrol.PestControlNoviceBoatArea;
import com.elvarg.game.model.areas.impl.pestcontrol.PestControlOutpostArea;
import com.elvarg.game.model.dialogues.DialogueExpression;
import com.elvarg.game.model.dialogues.entries.impl.NpcDialogue;
import com.elvarg.game.task.Task;
import com.elvarg.game.task.TaskManager;
import com.elvarg.util.NpcIdentifiers;
import com.google.common.collect.Lists;

import java.util.*;

import static com.elvarg.util.NpcIdentifiers.*;

public class PestControl implements Minigame {

    /**
     * TODO:
     * Spawn all NPCS outside arena
     * Make NPCs auto attack the gates, then void knight, defend portals
     * Waiting boat interface
     * Game area interface
     * Instanced games and support for novice/med/advanced boats
     * Gates need to open
     * Fence damaging and repairing
     * <p>
     * Handle gates
     * Fix NPC ids for portal spawns
     */
    public static final Area GAME_AREA = new PestControlArea();

    public static final Area OUTPOST_AREA = new PestControlOutpostArea();

    public static final Area NOVICE_BOAT_AREA = new PestControlNoviceBoatArea();

    /**
     * The tile which is right beside the gang plank.
     */
    public static final Location GANG_PLANK_START = new Location(2657, 2639, 0);

    /**
     * /** how long before were put into the game from lobby
     */
    private static final int WAIT_TIMER = 60;
    /**
     * How many players we need to start a game
     */
    private final static int PLAYERS_REQUIRED = 1;

    public static int gameTimer;
    public static final int DEFAULT_BOAT_WAITING_TICKS = 60;
    public static boolean gameStarted = false;

    private List<NPC> spawned_npcs = Lists.newArrayList();

    private List<PestControlPortal> portals = Arrays.asList(
            new PestControlPortal(3777, new Location(2628, 2591)),
            new PestControlPortal(3778, new Location(2680, 2588)),
            new PestControlPortal(3779, new Location(2669, 2570)),
            new PestControlPortal(3780, new Location(2645, 2569))
    );

    @Override
    public void init() {
        /** Knight in the middle **/
        spawnNPC(VOID_KNIGHT_GAME, new Location(2656, 2592));
        /** Squire to leave next to boat **/
        spawnNPC(SQUIRE_12, new Location(2655, 2607));
        /** Portal spawns **/
        portals.stream().forEach(p -> spawnNPC(p.id, p.location));

        PestControlBoat novice_boat = PestControlBoat.NOVICE;
        Task noviceLobbyTask = new Task(1, PestControlBoat.NOVICE.name()) {

            int noviceWaitTicks = DEFAULT_BOAT_WAITING_TICKS;
            @Override
            protected void execute() {

                int playersReady = novice_boat.getQueue().size();

                if (playersReady <= 1) {
                    noviceWaitTicks = DEFAULT_BOAT_WAITING_TICKS;
                    return;
                }

                noviceWaitTicks--;

                if (noviceWaitTicks == 0) {
                    noviceWaitTicks = DEFAULT_BOAT_WAITING_TICKS;

                    Queue<Player> queue = novice_boat.getQueue();

                    Iterator lobbyQueue = queue.iterator();

                    int movedPlayers = 0;
                    while (lobbyQueue.hasNext()) {
                        if (movedPlayers >= 25) {
                            break;
                        }
                        movedPlayers++;
                        Player player = queue.poll();
                        if (player != null) {
                            moveToGame(novice_boat, player);
                        }
                    }
                    if (queue.size() > 0) {
                        queue.forEach(p -> p.getPacketSender().sendMessage("You have been given priority for the next game!"));
                    }
                }

            }
        };
        TaskManager.submit(noviceLobbyTask);
    }

    private void moveToGame(PestControlBoat boat, Player player) {
        player.smartMove(PestControlArea.LAUNCHER_BOAT_BOUNDARY);
        NpcDialogue.sendStatement(player, NpcIdentifiers.VOID_KNIGHT, new String[] {"You must defend the Void Knight while the portals are", "unsummoned. The ritual takes twenty minutes though,", "so you can help out by destroying them yourselves!", "Now GO GO GO!" }, DialogueExpression.DISTRESSED);

        /**
         * gameStarted = true;
         * gameTimer = 400;
         */
    }

    /**
     * Determines whether the game is still active.
     *
     * @return
     */
    public boolean isActive() {
        return gameTimer > 0 && gameStarted;
    }

    public void spawnNPC(int id, Location pos) {
        /**
         * TODO support for private area
         */
        NPC npc = new NPC(id, pos);
        spawned_npcs.add(npc);
    }

    private static final int[][] PEST_CONTROL_MONSTERS = {
            {
                    1724,//Defiler - level 33
                    1726,//Defiler - level 50
            },
            {
                    1725,//Defiler - level 50
                    1727,//Defiler - level 66
                    1728,//Defiler - level 80
            },
            {
                    1730,//Defiler - level 80
                    1732,//Defiler - level 97
            }
    };

    @Override
    public boolean firstClickObject(Player player, GameObject object) {
        // All object handling should be done in Areas where possible
        return false;
    }

    @Override
    public boolean handleButtonClick(Player player, int button) {
        return false;
    }

    @Override
    public void process() {
        try {

            if (!isActive()) {
                return;
            }

            gameTimer--;

            if (playersInGame() < 1 || getKnightsHealth() == 0) {
                endGame(false);
            }

            /**
             * TODO check portal death..
             */


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getKnightsHealth() {
        /** For loop due to calling every tick **/
        for (NPC npc : spawned_npcs) {
            if (npc != null && npc.getId() == 1755)
                return npc.getHitpoints();
        }
        return 0;
    }

    /**
     * Checks how many players are in the game
     *
     * @return players in the game
     */
    private int playersInGame() {
        return GAME_AREA.getPlayers().size();
    }

    private void endGame(boolean won) {
        GAME_AREA.getPlayers().forEach(player -> {

            player.moveTo(new Location(2657, 2639, 0));

            Integer damage = (Integer) player.getAttribute("pcDamage");
            int myDamage = damage == null ? 0 : damage;

            int reward_points = 2;

            if (!won) {
                NpcDialogue.send(player, NpcIdentifiers.VOID_KNIGHT, "You failed to kill all the portals in 3 minutes and have not been awarded points.", DialogueExpression.CALM);
                player.getPacketSender().sendMessage("You failed to kill all the portals in 3 minutes and have not been awarded points.");
                return;
            }
            if (myDamage > 50) {
                NpcDialogue.send(player, NpcIdentifiers.VOID_KNIGHT, "Do not let the Void Knights health reach 0!" +
                        "You can regain health by destroying more monsters,", DialogueExpression.SLIGHTLY_SAD);
                reward_points = 4;
                player.getPacketSender().sendMessage(
                        "You have won the pest control game and have been awarded "
                                + reward_points + " Pest Control points.");
                player.pcPoints += reward_points;
                return;
            }
            NpcDialogue.send(player, NpcIdentifiers.VOID_KNIGHT, "The void knights notice your lack of zeal. You only gain " + reward_points + " points.", DialogueExpression.DISTRESSED);
            player.pcPoints += reward_points;
        });

        cleanUp();
    }

    /**
     * Resets the game variables and map
     */
    private void cleanUp() {
        gameTimer = -1;
        gameStarted = false;
        spawned_npcs.stream().filter(n -> n != null).forEach(n -> n.setDying(true));
        spawned_npcs.clear();
    }

    private static boolean isQueued(Player player, PestControlBoat boat) {
        return boat.getQueue().contains(player);
    }



    private static void addToQueue(Player player, PestControlBoat boat) {
        if (isQueued(player, boat)) {
            System.err.println("Error.. adding " + player.getUsername() + " to " + boat.name() + " list.. already on the list.");
            return;
        }
        /**
         * TODO.. might be a good idea to get the players in the area then add all to the list.. however.. pest control uses a queue system not list!
         */
        boat.getQueue().add(player);
    }

    /**
     * Moves a player into the hash and into the lobby
     *
     * @param player The player
     */
    public static void addToWaitingRoom(Player player, PestControlBoat boat) {
        player.getPacketSender().sendMessage("You have joined the Pest Control boat.");
        player.getPacketSender().sendMessage("You currently have " + player.pcPoints + " Pest Control Points.");
        player.getPacketSender().sendMessage("Players needed: " + PLAYERS_REQUIRED + " to 25 players.");
        addToQueue(player, boat);
        player.moveTo(boat.enterBoatLocation);
    }


}
