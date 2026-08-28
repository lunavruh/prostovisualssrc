local dronezAPI = require("dronezAPI")
local cfg = require("pv_pet_config")
local behavior = (cfg and cfg.behavior) or "follow"

-- Responsive but still soft: enough acceleration to react within a few ticks,
-- while friction/braking keep the movement from snapping or oscillating.
local droneHandle = dronezAPI.new(models.mothli.World)
    :setTopSpeed(0.52)
    :setAcceleration(0.085)
    :setAirFriction(0.94)
    :setBrakeMultiplier(3.5)
    :setStopThreshold(0.055)
    :setWarpThreshold(math.huge)
    :setSyncEnabled(false)

local assistTarget = nil
local assistTicks = 0
local orbitAngle = 0.0

local function ownerBase()
    if not player:isLoaded() then return vec(0,0,0) end
    return player:getPos() + vec(0, math.max(0.95, player:getEyeHeight() - 0.40), 0)
end

local function flattenedLook()
    local look = player:getLookDir()
    local flat = vec(look.x, 0, look.z)
    if flat:length() < 0.001 then return vec(0,0,1) end
    return flat:normalize()
end

local function rightVector(look)
    -- Horizontal right-hand vector. Used so "front" never blocks the crosshair.
    return vec(-look.z, 0, look.x)
end

local function targetPosition(droneObj)
    local base = ownerBase()
    local look = flattenedLook()
    local right = rightVector(look)

    if behavior == "front" then
        -- Front-right shoulder position, intentionally outside the centre of the view.
        return base + look * 0.88 + right * 0.92 + vec(0, -0.05, 0)
    elseif behavior == "orbit" then
        local radius = 1.62
        return base + vec(math.cos(orbitAngle) * radius, 0.10 + math.sin(orbitAngle * 2.0) * 0.08, math.sin(orbitAngle) * radius)
    elseif behavior == "assist" and assistTarget and assistTarget:isLoaded() and assistTicks > 0 then
        return assistTarget:getPos() + vec(0, math.max(0.9, assistTarget:getBoundingBox().y + 0.20), 0)
    end

    -- Follow from a close rear-side position. Small owner movements already change the
    -- target, so the pet starts following immediately rather than after several blocks.
    return base - look * 0.82 + right * 0.48 + vec(0, 0.05, 0)
end

droneHandle:setTargetPosFunction(targetPosition)

-- Cosmetic pet: never apply DronezAPI punch impulse.
function droneHandle.dronePunched(droneObj, interactor)
    return false
end

function droneHandle.droneWarp(droneObj)
    -- ProstoVisual pets never teleport. They always fly to the target naturally.
    return false
end

function droneHandle.dronePostWarp(droneObj)
    -- Intentionally empty: no teleport SFX for cosmetic pets.
end

function droneHandle.droneInteracted(droneObj, interactor)
    if interactor and interactor:isSneaking() then return false end
    if animations.mothli and animations.mothli.pat then animations.mothli.pat:play() end
    sounds:playSound("minecraft:entity.cat.purr", droneObj.pos, 0.55, 1.05)
    particles:newParticle("happy_villager", droneObj.pos + vec(0, 0.45, 0))
    droneObj.patTimer = 20
end

local function livingNotOwner(entity)
    return entity ~= player and entity:isLiving()
end

events.TICK:register(function()
    if not player:isLoaded() then return end

    -- Orbit only advances the target point. DronezAPI itself performs the acceleration/braking.
    -- The old code also wrote velocity here, so two controllers fought each other and Mothli
    -- oscillated wildly when the player stopped.
    if behavior == "orbit" then
        orbitAngle = (orbitAngle + 0.050) % (math.pi * 2.0)
    end

    if behavior == "assist" and player:getSwingTime() == 1 then
        local startPos = player:getPos() + vec(0, player:getEyeHeight(), 0)
        local rayEnd = startPos + player:getLookDir() * 5.5
        local _, blockHit = raycast:block(startPos, rayEnd)
        local entity = raycast:entity(startPos, blockHit or rayEnd, livingNotOwner)
        if entity then
            assistTarget = entity
            assistTicks = 32
        end
    end

    if assistTicks > 0 then assistTicks = assistTicks - 1 else assistTarget = nil end

    local speed = droneHandle.velocity:length()
    if animations.mothli then
        if animations.mothli.fly then animations.mothli.fly:setPlaying(speed < 0.12) end
        if animations.mothli.fly_fast then animations.mothli.fly_fast:setPlaying(speed >= 0.12) end
    end

    if droneHandle.patTimer and droneHandle.patTimer > 0 then
        droneHandle.patTimer = droneHandle.patTimer - 1
        if droneHandle.patTimer <= 0 and animations.mothli and animations.mothli.pat then
            animations.mothli.pat:stop()
        end
    end
end, "prostovisual_mothli_behavior")
