# Fix Shop Creation Issue - Players Can't Make or Use Shops

## Problem Analysis
Players cannot make or use shops due to a race condition in ShopHandler.java where the getShopLocations(UUID player) method is not thread-safe. This causes shops to not be properly tracked in the playerShops map, leading to shops appearing to not exist when players try to use them.

## Root Cause
In ShopHandler.java, the getShopLocations(UUID player) method:
```java
private List<Location> getShopLocations(UUID player){
    List<Location> shopLocations;
    if(playerShops.containsKey(player)) {
        shopLocations = playerShops.get(player);
    }
    else
        shopLocations = new ArrayList<>();
    return shopLocations;
}
```

This method is not thread-safe because:
1. Multiple threads can simultaneously see that a player doesn't have a shop list
2. Each thread creates a new ArrayList
3. Each thread modifies their own list and puts it back into the map
4. Later puts overwrite earlier ones, causing loss of shop data

Additionally, in addShop() and removeShop() methods, we get the list, modify it, and put it back, which is not atomic and can lead to lost updates.

## Solution
Make the getShopLocations(UUID player) method thread-safe by using ConcurrentHashMap.computeIfAbsent, similar to what was already done for the chunkShops map in commit 166c060.

## Tasks

### Task 1: Fix getShopLocations(UUID player) method
- Replace the current implementation with a thread-safe version using computeIfAbsent
- This ensures that only one ArrayList is created per player UUID and that access to the list is properly coordinated

### Task 2: Verify the fix doesn't break existing functionality
- Ensure that the chunkShops getShopLocations method remains unchanged (it's already fixed)
- Verify that all other uses of getShopLocations still work correctly

### Task 3: Test the fix
- Compile the code to ensure no syntax errors
- Run existing tests if available
- Manually verify the logic is correct

## Expected Result
After the fix, players should be able to create and use shops without issues, as the playerShops map will properly track which shops belong to which players in a thread-safe manner.