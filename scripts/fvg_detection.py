import datetime
from typing import List, Tuple


def detect_fvg_after_asia_break(candles: List[Tuple[datetime.datetime, float, float, float, float]],
                                asia_start: datetime.time = datetime.time(0, 0),
                                asia_end: datetime.time = datetime.time(9, 0)):
    """Detect fair value gaps (FVG) that form only after price breaks the
    Asian session range.

    Args:
        candles: List of tuples (timestamp, open, high, low, close) sorted by time.
        asia_start: start time of the Asian session (inclusive).
        asia_end: end time of the Asian session (exclusive).

    Returns:
        A list of dictionaries describing FVG occurrences after the
        Asian range break. Each dict contains:
        {
            "time": timestamp of the candle where the gap appears,
            "direction": "up" or "down",
            "gap": (gap_high, gap_low)
        }
    """

    if not candles:
        return []

    # Determine Asia range for the first session in the dataset
    asia_high = float('-inf')
    asia_low = float('inf')
    asia_end_idx = None

    for i, (ts, _o, high, low, _c) in enumerate(candles):
        t = ts.time()
        if asia_start <= t < asia_end:
            asia_high = max(asia_high, high)
            asia_low = min(asia_low, low)
            asia_end_idx = i
        elif t >= asia_end and asia_end_idx is not None:
            # we have processed the Asia session
            break

    if asia_end_idx is None:
        return []

    breakout_dir = None
    results = []

    # Check candles after the Asian session for breakout and FVG
    for i in range(asia_end_idx + 1, len(candles)):
        ts, _o, high, low, _c = candles[i]
        if breakout_dir is None:
            if high > asia_high:
                breakout_dir = 'up'
            elif low < asia_low:
                breakout_dir = 'down'
            continue

        prev_ts, _prev_o, prev_high, prev_low, _prev_c = candles[i - 1]

        if breakout_dir == 'up':
            if low > prev_high:  # upward gap
                results.append({
                    'time': ts,
                    'direction': 'up',
                    'gap': (prev_high, low)
                })
                # break after first FVG in direction
                break
        elif breakout_dir == 'down':
            if high < prev_low:  # downward gap
                results.append({
                    'time': ts,
                    'direction': 'down',
                    'gap': (high, prev_low)
                })
                break

    return results


if __name__ == '__main__':
    # Example usage with dummy data (hourly candles)
    candles = []
    start = datetime.datetime(2024, 1, 1, 0, 0)
    for i in range(24):
        ts = start + datetime.timedelta(hours=i)
        # Dummy high/low values for demonstration
        o = i
        h = i + 1
        l = i - 1
        c = i + 0.5
        candles.append((ts, o, h, l, c))

    gaps = detect_fvg_after_asia_break(candles)
    for g in gaps:
        print(f"FVG detected at {g['time']} direction {g['direction']} gap {g['gap']}")
