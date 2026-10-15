/* Coded by Afrobama/Claude */
package com.afrobama.bbstools.client.bbs.fastpath;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

/**
 * One actor's per-frame work item for the parallel film controller path.
 *
 * Lives outside {@code ...client.mixin.*} on purpose — see {@link EligibleReplay} for the crash
 * that a nested record inside a mixin package causes (IllegalClassLoadError as soon as the woven
 * code touches it).
 */
public record ActorWork(int i, Replay replay, IEntity entity, int entityId, float tickPlusDelta)
{}
