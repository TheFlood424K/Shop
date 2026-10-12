Fixes multiple issues from the bug-fix campaign:

## Issues Fixed

### P0 (Critical)
- **#151**: Sign price validation allows zero price for SELL/BUY/COMBO shops but chat creation rejects it — inconsistency
- **#152**: BuyShop and ComboShop treat price=0 as infinite stock (admin-like) — exploit with sign creation allowing zero price
- **#153**: ShopMessage.getCreationWord() fallback capitalizes but config uses lowercase — breaks shop type parsing if config key missing
- **#136**: Transaction error messages may be silently unreachable: message key fallback needs verification
- **#161**: PlayerNameCache.initialize() never called — cache file saved but never loaded on startup

### P1 (High)
- **#158**: [stock color] placeholder handling fragile — only supports & codes, not hex/named colors
- **#147**: ShopMessage.getUnformattedMessage() receives shop types in 3 inconsistent shapes — fragile fallback logic
- **#146**: signConfig.yml uses inconsistent casing for shop type keys (lowercase in sign_creation, uppercase in sign_text)
- **#154**: CI uses -DfailIfNoTests=false — allows zero-test builds to pass (surefire 3.6.0 bug vector)
- **#141**: ShopType.toString() returns lowercase but config sections use uppercase — silent message lookup failures
- **#143**: Ownership checks must compare by UUID, not name — add regression test
- **#142**: Two config tables (creation_words vs sign_creation) look identical but are not interchangeable — silent bug risk

### Additional Fixes
- Implement double chest support for chest-based shop creation
- Fix barter item creative selection logic
- Remove outdated TODO comment in TransactionHandler
- Add /tx to tab completion
- PluginDescriptorConsistencyTest for plugin.yml consistency
- LogHandler purgeOldActions for offline players

## Changes Made

### Core Fixes
1. **ShopCreationUtil.java**: Aligned sign price validation with chat validation - reject zero primary price for all transactional types (BUY, SELL, COMBO, BARTER), only allow for GAMBLE. Also validate priceCombo == 0 for COMBO shops.

2. **BuyShop.java & ComboShop.java**: Removed `getPrice() == 0` special case that set stock to Integer.MAX_VALUE (infinite). This was creating a silent free-item exploit.

3. **ShopMessage.java**:
   - Fixed `getCreationWord()` fallback to use lowercase instead of capitalized
   - Added `getShopMessage(ShopType, String, String)` for standardized message lookup
   - Added `sendMessage(ShopType, String, String, Player, AbstractShop)` overloads
   - Fixed `getShopSignText()` to use consistent uppercase keys
   - Updated `format()` to handle hex and named colors in `[stock color]` placeholder
   - Made `isColorDirective()` package-private for test access
   - Updated `getShopTypesPlaceholder()` to use `getConfigKey()`

3. **ShopType.java**: Added `getConfigKey()` method returning uppercase name for config consistency

4. **TransactionHandler.java**: Updated to use new standardized `getShopMessage()` and `sendMessage()` methods with ShopType

5. **Shop.java**: Added call to `PlayerNameCache.initialize()` in `onEnable()`

6. **signConfig.yml**: Standardized to use consistent uppercase casing for shop types throughout

7. **MiscListener.java & CreativeSelectionListener.java**: Updated to use `ShopType.getConfigKey()` and new `sendMessage()` overloads

8. **LogHandler.java**: Fixed `purgeOldActions()` SQL to properly keep transactions for owners without summary entries (never logged in) until they first log in

9. **MiscListener.java**: Implemented double chest support for chest-based shop creation; fixed barter item creative selection logic; removed outdated TODO comment

9. **CommandHandler.java**: Added "/tx" to tab completion

10. **.github/workflows/build.yml**: Changed `-DfailIfNoTests=false` to `-DfailIfNoTests=true`

10. **pom.xml**: Updated dependency versions (log4j-api 2.27.0, plexus-utils 4.2.0, commons-lang3 3.21.0) to address Dependabot security alerts

### Tests
- **StockColorPlaceholderTest.java**: 8 tests for hex, named, and legacy color codes
- **OwnerNotificationBrandTagTest.java**: 4 tests verifying owner notifications use literal "[Shop]" brand tag
- **ShopOwnershipByUUIDTest.java**: 3 tests verifying ownership checks use UUID comparison
- **ShopTransactionsTest.java** and **PricePairValidationTest.java**: Updated to reflect new validation rules
- **PluginDescriptorConsistencyTest.java**: New test ensuring test/resources/plugin.yml stays in sync with main/resources/plugin.yml
- Updated **ForkAuditListenerRegressionTest.java** and **MiscListenerTest.java** to use `getSignCreationWord()`

### Verification
- All 443 tests pass (0 failures)
- Build succeeds

🤖 Generated with [Claude Code](https://claude.com/claude-code)