Issue #187: Shop Creation Timeout Configurable

Current State Analysis:

MiscListener.java has two hardcoded creation timeouts:
- Sign creation (lines 158-173): 30 * 20 ticks (30 seconds)
- Chest creation (lines 374-384): 30 * 20 ticks (30 seconds)

Shop.java contains debug_shopCreateCooldown (different purpose - cooldown between creation processes).

config.yml has debug.shopCreateCooldown: 5000 (5 seconds) but no creation timeout setting.

signConfig.yml has timeout messages but no configurable timeout value.

Required Changes:
1. Add creation timeout config in config.yml
2. Modify MiscListener.java to read timeout from config instead of hardcoding 30*20 ticks
3. Optional: Add Shop.java getter for creation timeout

Key files:
- MiscListener.java:158-173 (sign creation timeout)
- MiscListener.java:374-384 (chest creation timeout)
- config.yml:450 (existing debug.cooldown setting)
- signConfig.yml:160-163 (timeout messages)