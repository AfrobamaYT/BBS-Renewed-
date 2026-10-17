#!/usr/bin/env bash
set -euo pipefail
test "$#" -gt 0 || { echo 'A production test command is required' >&2; exit 1; }
case "${BBS_RECORD_PRODUCTION_VIDEO:-0}" in
    0) exec "$@" ;;
    1) ;;
    *) echo 'Video recording requires an explicit 0 or 1' >&2; exit 1 ;;
esac
if [[ "${GITHUB_ACTIONS:-}" != true || "${RUNNER_OS:-}" != Linux || "${RUNNER_ENVIRONMENT:-}" != github-hosted ]]; then
    echo 'Production screen recording is restricted to the isolated Linux CI display' >&2
    exit 1
fi
: "${DISPLAY:?A private Xvfb display is required}"
command -v ffmpeg >/dev/null
command -v ffprobe >/dev/null
record_dir=build/production-diagnostics
mkdir -p "$record_dir"
date +%s%3N > "$record_dir/recording-start-epoch-ms.txt"
ffmpeg -hide_banner -loglevel error -nostdin -y -f x11grab -draw_mouse 0 -framerate 20 \
    -video_size 1280x720 -i "$DISPLAY" -an -c:v libx264 -threads 1 -preset ultrafast \
    -crf 23 -pix_fmt yuv420p "$record_dir/production-screen.mp4" \
    > "$record_dir/production-screen-encoder.log" 2>&1 &
record_pid=$!
finish_recording() {
    local command_status=$? encoder_status=0
    trap - EXIT
    if kill -0 "$record_pid" 2>/dev/null; then
        kill -INT "$record_pid"
        wait "$record_pid" || encoder_status=$?
        if [[ "$encoder_status" != 0 && "$encoder_status" != 255 ]]; then command_status=1; fi
    else
        echo 'Screen encoder ended before the production command' >&2
        command_status=1
    fi
    if ! ffprobe -v error -select_streams v:0 -show_entries stream=width,height,avg_frame_rate,nb_frames,duration \
        -of json "$record_dir/production-screen.mp4" > "$record_dir/production-screen-metadata.json"; then
        command_status=1
    fi
    exit "$command_status"
}
trap finish_recording EXIT
sleep 0.5
kill -0 "$record_pid"
"$@"
