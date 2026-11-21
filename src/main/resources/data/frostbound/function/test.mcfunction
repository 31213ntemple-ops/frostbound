# /function frostbound:test
# Front row = Frozen, back row = normal, for side-by-side comparison.
summon minecraft:zombie ~-6 ~ ~4 {Frostbound:1b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Frozen Zombie"'}
summon minecraft:skeleton ~-3 ~ ~4 {HandItems:[{id:"minecraft:bow",count:1},{}],Frostbound:1b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Frozen Skeleton"'}
summon minecraft:spider ~0 ~ ~4 {Frostbound:1b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Frozen Spider"'}
summon minecraft:creeper ~3 ~ ~4 {Frostbound:1b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Frozen Creeper"'}
summon minecraft:slime ~6 ~ ~4 {Frostbound:1b,Size:2,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Frozen Slime"'}
summon minecraft:zombie ~-6 ~ ~8 {Frostbound:0b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Normal Zombie"'}
summon minecraft:skeleton ~-3 ~ ~8 {HandItems:[{id:"minecraft:bow",count:1},{}],Frostbound:0b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Normal Skeleton"'}
summon minecraft:spider ~0 ~ ~8 {Frostbound:0b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Normal Spider"'}
summon minecraft:creeper ~3 ~ ~8 {Frostbound:0b,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Normal Creeper"'}
summon minecraft:slime ~6 ~ ~8 {Frostbound:0b,Size:2,PersistenceRequired:1b,CustomNameVisible:1b,CustomName:'"Normal Slime"'}
tellraw @s {"text":"Frostbound test lineup spawned: frozen row in front, normal row behind.","color":"aqua"}
