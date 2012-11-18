package net.javachallenge.players;

import java.util.*;

import net.javachallenge.api.*;
import net.javachallenge.api.command.*;

public class Hasi extends ComputerPlayer {
	Random rand = new Random("hasi".hashCode());
	int median;
	HashMap<TrianglePoint, ArrayList<TrianglePoint>> G = null;

	@Override
	public String getName() {
		return "hasi";
	}

	@Override
	public TrianglePoint selectVein(Game game) {
		this.saveTemporalVeinLocation(Make.point(0, 0));

		if (G == null) {
			G = new HashMap<TrianglePoint, ArrayList<TrianglePoint>>();
			ArrayList<Vein> veinsAll = game.getField().getVeins();
			int N = veinsAll.size();
			int[][] distanceTable = new int[N][N];
			for (int i = 1; i < N; ++ i) {
				for (int j = 0; j < i; ++ j) {
					distanceTable[i][j] = distanceTable[j][i] = veinsAll.get(i).getDistance(veinsAll.get(j));
				}
			}
			boolean[][] notShortest = new boolean[N][N];
			for (int i = 1; i < N; ++ i) {
				for (int j = 0; j < i; ++ j) {
					for (int k = 0; k < N; ++ k) if (k != i && k != j) {
						if (distanceTable[i][j] >= distanceTable[i][k] + distanceTable[k][j]) {
							notShortest[i][j] = notShortest[j][i] = true;
							break;
						}
					}
				}
			}
			ArrayList<Integer> cnt = new ArrayList<Integer>();
			for (int i = 0; i < N; ++ i) {
				ArrayList<TrianglePoint> a = new ArrayList<TrianglePoint>();
				for (int j = 0; j < N; ++ j) {
					if (j != i && !notShortest[i][j]) {
						a.add(veinsAll.get(j).getLocation());
					}
				}
				G.put(veinsAll.get(i).getLocation(), a);
				cnt.add(a.size());
			}
			Collections.sort(cnt);
			median = cnt.get(cnt.size() / 2);
		}

		ArrayList<Vein> veins = new ArrayList<Vein>();
	    for (Vein vein : game.getField().getVeins(game.getNeutralPlayerId())) {
			veins.add(vein);
	    }
	    if (!veins.isEmpty()) {
	    	Collections.sort(veins, new SelectVeinComparator(game));
	    	return veins.get(0).getLocation();
	    }
	    for (Vein vein : game.getField().getVeins()) {
			if (vein.getOwnerId() == game.getNeutralPlayerId()) {
				veins.add(vein);
			}
	    }
	    return veins.get(rand.nextInt(veins.size())).getLocation();
	}

	@Override
	public List<Command> selectActions(Game game) {
		List<Command> commands = new ArrayList<Command>();
		// Upgrade
		ArrayList<Vein> veins = new ArrayList<Vein>();
		for (Vein vein : game.getField().getVeins(game.getMyPlayerId())) {
			veins.add(vein);
		}
    	Collections.sort(veins, new UpgradeVeinComparator());
    	HashMap<Material, Integer> amount = new HashMap<Material, Integer>();
		for (Material material : Material.values()) {
			amount.put(material, game.getMyPlayer().getMaterial(material));
		}
		loop1: for (Vein vein : veins) {
			if (vein.getRobotRank() == 1) {
				for (Material material : Material.values()) {
					if (amount.get(material) < game.getSetting().getMaterialsForUpgradingRobotRankFrom1To2(material)) continue loop1;
				}
				for (Material material : Material.values()) {
					amount.put(material, amount.get(material) - game.getSetting().getMaterialsForUpgradingRobotRankFrom1To2(material));
				}
				commands.add(Commands.upgradeRobot(vein));
			} else if (vein.getRobotRank() == 2) {
				for (Material material : Material.values()) {
					if (amount.get(material) < game.getSetting().getMaterialsForUpgradingRobotRankFrom2To3(material)) continue loop1;
				}
				for (Material material : Material.values()) {
					amount.put(material, amount.get(material) - game.getSetting().getMaterialsForUpgradingRobotRankFrom2To3(material));
				}
				commands.add(Commands.upgradeRobot(vein));
			}
		}
		// Trade
		if (amount.get(Material.Metal) + amount.get(Material.Gas) < 1500 && amount.get(Material.Stone) > 0) {
			commands.add(Commands.sellToAlienTrade(Material.Stone, amount.get(Material.Stone)));
		}
		if (game.getMyPlayer().getMoney() > 0) {
			Material minMaterial = amount.get(Material.Metal) < 1000 ? Material.Metal : Material.Gas;
			int x = game.getMyPlayer().getMoney() / game.getAlienTrade().getBuyPriceOf(minMaterial);
			if (x > 0) commands.add(Commands.buyFromAlienTrade(minMaterial, x));
		}
		// Launch
		HashMap<TrianglePoint, Integer> posNeighbors = new HashMap<>();
		HashMap<TrianglePoint, Boolean> hasNeighbors = new HashMap<>();
		int numRobots = 0;
		for (Vein vein : game.getField().getVeins(game.getMyPlayerId())) {
			numRobots += vein.getNumberOfRobots();
			for (TrianglePoint pos : G.get(vein.getLocation())) {
				Vein toVein = game.getField().getVein(pos);
				if (toVein.getOwnerId() != game.getMyPlayerId()) {
					posNeighbors.put(pos, toVein.getNumberOfRobots());
					hasNeighbors.put(vein.getLocation(), true);
				}
			}
		}
		ArrayList<Vein> frontline = new ArrayList<Vein>();
		for (Vein vein : game.getField().getVeins(game.getMyPlayerId())) {
			if (!hasNeighbors.containsKey(vein.getLocation())) {
				int x = vein.getNumberOfRobots() - numRobots / game.getSetting().getVeinCount();
				if (x > 0) {
					int minDist = Integer.MAX_VALUE;
					ArrayList<TrianglePoint> targetVein = new ArrayList<TrianglePoint>();
					for (Vein v : game.getField().getVeins(game.getMyPlayerId())) {
						if (hasNeighbors.containsKey(v.getLocation())) {
							if (vein.getDistance(v) < minDist) {
								minDist = vein.getDistance(v);
								targetVein = new ArrayList<TrianglePoint>();
							}
							if (vein.getDistance(v) <= minDist) {
								targetVein.add(v.getLocation());
							}
						}
					}
					HashSet<TrianglePoint> toVein = new HashSet<TrianglePoint>();
					for (TrianglePoint p : targetVein) {
						Vein v = game.getField().getVein(p);
						int d = Integer.MAX_VALUE;
						ArrayList<Vein> a = new ArrayList<Vein>();
						for (Vein t : game.getField().getVeins(game.getMyPlayerId())) {
							if (t != vein && vein.getDistance(t) <= d && vein.getDistance(t) + t.getDistance(v) <= vein.getDistance(v)) {
								if (vein.getDistance(t) < d) {
									d = vein.getDistance(t);
									a = new ArrayList<Vein>();
								}
								a.add(t);
							}
						}
						if (a.isEmpty()) {
							toVein.add(v.getLocation());
						} else {
							for (Vein t : a) toVein.add(t.getLocation());
						}
					}
					if (x >= toVein.size()) for (TrianglePoint p : toVein) {
						Vein v = game.getField().getVein(p);
						commands.add(Commands.launch(x / toVein.size(), vein.getLocation(), v.getLocation()));
					}
				}
			} else {
				frontline.add(vein);
			}
		}
		ArrayList<Vein> targets = new ArrayList<Vein>();
		for (Map.Entry<TrianglePoint, Integer> e : posNeighbors.entrySet()) {
			targets.add(game.getField().getVein(e.getKey()));
		}
		HashMap<TrianglePoint, Integer> squads = new HashMap<TrianglePoint, Integer>();
		for (Squad squad : game.getField().getSquads(game.getMyPlayerId())) {
			TrianglePoint p = squad.getDestinationLocation();
			if (!squads.containsKey(p)) {
				squads.put(p, 0);
			}
			squads.put(p, squads.get(p) + squad.getRobot());
		}
		for (Vein vein : frontline) {
	    	int num = 0;
	    	int minDist = Integer.MAX_VALUE;
	    	Vein toVein = null;
	    	boolean existsNeutral = false;
	    	Collections.sort(targets, new TargetVeinComparator(game, vein, squads));
	    	for (Vein v : targets) {
	    		if (existsNeutral && v.getOwnerId() == game.getNeutralPlayerId()) {
	    			int distance = vein.getDistance(v);
	    			if (distance < minDist) {
	    				minDist = distance;
	    				num = 1;
    					toVein = v;
	    			} else if (distance == minDist) {
	    				if (rand.nextInt(++ num) == 0) {
	    					toVein = v;
	    				}
	    			}
	    		} else if (v.getOwnerId() == game.getNeutralPlayerId()) {
	    			existsNeutral = true;
    				minDist = vein.getDistance(v);
    				num = 1;
					toVein = v;
	    		} else if (v.getOwnerId() != game.getMyPlayerId()) {
	    			int distance = vein.getDistance(v);
	    			if (distance < minDist) {
	    				minDist = distance;
	    				num = 1;
    					toVein = v;
	    			} else if (distance == minDist) {
	    				if (rand.nextInt(++ num) == 0) {
	    					toVein = v;
	    				}
	    			}
	    		}
	    	}
	    	if (num > 0) {
	    		int x = toVein.getNumberOfRobots();
	    		if (toVein.getOwnerId() != game.getNeutralPlayerId()) {
	    			x += toVein.getCurrentRobotProductivity() * vein.getDistance(toVein);
	    		}
	    		if (x < vein.getNumberOfRobots() - 1) { 
	    			TrianglePoint p = toVein.getLocation();
	    			x = Math.max(x + 1, vein.getNumberOfRobots() / 2);
					commands.add(Commands.launch(x, vein.getLocation(), p));
					if (!squads.containsKey(p)) {
						squads.put(p, 0);
					}
					squads.put(p, squads.get(p) + x);
	    		}
	    	}
		}

		return commands;
	}

	class SelectVeinComparator implements Comparator<Vein> {
		HashMap<Material, Integer> weightMatrial = new HashMap<Material, Integer>();
		Game game;

		SelectVeinComparator(Game game) {
			this.game = game;
			for (Material material : Material.values()) {
				weightMatrial.put(material, 0);
			}
			if (game.getField().getVeins(game.getMyPlayerId()).isEmpty()) {
				weightMatrial.put(Material.Gas, 2);
				weightMatrial.put(Material.Metal, 1);
			} else {
				weightMatrial.put(Material.Metal, 2);
				weightMatrial.put(Material.Gas, 1);
			}
		}
		
		public int score(Vein v) {
			int score = 0;
			for (Vein to : game.getField().getVeins()) {
				if (to.getOwnerId() != v.getOwnerId()) {
					if (to.getOwnerId() == game.getNeutralPlayerId()) {
						score += (20 - v.getDistance(to)) * 100;
					} else {
						score -= (20 - v.getDistance(to)) * 500;
					}
				} else if (to != v) {
					score -= (20 - v.getDistance(to)) * 100;
				}
			}
			score += weightMatrial.get(v.getMaterial()) * 1000;
			score += v.getInitialRobotProductivity() * 100;
			score += v.getNumberOfRobots();
			return score;
		}

		@Override
		public int compare(Vein a, Vein b) {
			return -new Integer(score(a)).compareTo(score(b));
		}

	};

	class UpgradeVeinComparator implements Comparator<Vein> {

		@Override
		public int compare(Vein a, Vein b) {
			if (a.getCurrentRobotProductivity() != b.getCurrentRobotProductivity()) {
				return -new Integer(a.getCurrentRobotProductivity()).compareTo(b.getCurrentRobotProductivity());
			}
			return 0;
		}

	};

	class TargetVeinComparator implements Comparator<Vein> {
		Game game;
		Vein from;
		HashMap<TrianglePoint, Integer> squads;

		TargetVeinComparator(Game game, Vein from, HashMap<TrianglePoint, Integer> squads) {
			this.game = game;
			this.from = from;
			this.squads = squads;
		}
		
		public int score(Vein v) {
			int score = 0;
			if (v.getOwnerId() == game.getNeutralPlayerId()) {
				if (squads.containsKey(v.getLocation())) {
					if (squads.get(v.getLocation()) <= v.getNumberOfRobots()) {
						score += 10000;
					} else {
						score += 5000;
					}
				} else {
					score += 10000;
				}
			} else {
				if (squads.containsKey(v.getLocation())) {
					if (squads.get(v.getLocation()) + 100 <= v.getNumberOfRobots()) {
						score += 500;
					}
				} else {
					score += 500;
				}
			}
			score -= from.getDistance(v);
			return score;
		}

		@Override
		public int compare(Vein a, Vein b) {
			return -new Integer(score(a)).compareTo(score(b));
		}

	};

}
