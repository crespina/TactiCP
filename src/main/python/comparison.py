from pathlib import Path
from typing import Dict, List

import argparse
import json
import re
import subprocess
import numpy as np


def extract_time_as_float_seconds(value: str) -> float:
	match = re.search(r"(\d+(?:\.\d+)?)", value)
	if not match:
		raise ValueError(f"Could not parse time from: {value}")
	return float(match.group(1))


def extract_times_dict_as_float_seconds(raw_times: Dict[str, str]) -> Dict[str, float]:
	return {key: extract_time_as_float_seconds(value) for key, value in raw_times.items()}


def predict_exec_times():
	raw = {'SNGS-021': 'Time: 7.94165 seconds', 'SNGS-046': 'Time: 7.75390 seconds', 'SNGS-071': 'Time: 7.71579 seconds', 'SNGS-096': 'Time: 7.73183 seconds', 'SNGS-121': 'Time: 7.83951 seconds', 'SNGS-146': 'Time: 7.79771 seconds', 'SNGS-187': 'Time: 7.57929 seconds', 'SNGS-022': 'Time: 7.70172 seconds', 'SNGS-047': 'Time: 7.60367 seconds', 'SNGS-072': 'Time: 7.77032 seconds', 'SNGS-097': 'Time: 7.78884 seconds', 'SNGS-122': 'Time: 7.77288 seconds', 'SNGS-147': 'Time: 7.72985 seconds', 'SNGS-188': 'Time: 7.72803 seconds', 'SNGS-023': 'Time: 7.78175 seconds', 'SNGS-048': 'Time: 7.76096 seconds', 'SNGS-073': 'Time: 7.78700 seconds', 'SNGS-098': 'Time: 7.63080 seconds', 'SNGS-123': 'Time: 7.70299 seconds', 'SNGS-148': 'Time: 7.74811 seconds', 'SNGS-189': 'Time: 7.56386 seconds', 'SNGS-024': 'Time: 7.76987 seconds', 'SNGS-049': 'Time: 7.76645 seconds', 'SNGS-074': 'Time: 7.88444 seconds', 'SNGS-099': 'Time: 7.75830 seconds', 'SNGS-124': 'Time: 7.72207 seconds', 'SNGS-149': 'Time: 7.61114 seconds', 'SNGS-190': 'Time: 7.67767 seconds', 'SNGS-025': 'Time: 7.73732 seconds', 'SNGS-050': 'Time: 7.61639 seconds', 'SNGS-075': 'Time: 7.62245 seconds', 'SNGS-100': 'Time: 7.58311 seconds', 'SNGS-125': 'Time: 7.59121 seconds', 'SNGS-150': 'Time: 7.70011 seconds', 'SNGS-191': 'Time: 7.59662 seconds', 'SNGS-026': 'Time: 7.71529 seconds', 'SNGS-051': 'Time: 7.61682 seconds', 'SNGS-076': 'Time: 7.73171 seconds', 'SNGS-101': 'Time: 7.63131 seconds', 'SNGS-126': 'Time: 7.64024 seconds', 'SNGS-151': 'Time: 7.80487 seconds', 'SNGS-192': 'Time: 7.72615 seconds', 'SNGS-027': 'Time: 7.65501 seconds', 'SNGS-052': 'Time: 7.71012 seconds', 'SNGS-077': 'Time: 7.70594 seconds', 'SNGS-102': 'Time: 7.75361 seconds', 'SNGS-127': 'Time: 7.58370 seconds', 'SNGS-152': 'Time: 7.77540 seconds', 'SNGS-193': 'Time: 7.70899 seconds', 'SNGS-028': 'Time: 7.61822 seconds', 'SNGS-053': 'Time: 7.61526 seconds', 'SNGS-078': 'Time: 7.58280 seconds', 'SNGS-103': 'Time: 7.91014 seconds', 'SNGS-128': 'Time: 7.66040 seconds', 'SNGS-153': 'Time: 7.73328 seconds', 'SNGS-194': 'Time: 7.60817 seconds', 'SNGS-029': 'Time: 7.73034 seconds', 'SNGS-054': 'Time: 7.73289 seconds', 'SNGS-079': 'Time: 7.65106 seconds', 'SNGS-104': 'Time: 7.72004 seconds', 'SNGS-129': 'Time: 7.77689 seconds', 'SNGS-154': 'Time: 7.73260 seconds', 'SNGS-195': 'Time: 7.60310 seconds', 'SNGS-030': 'Time: 7.75321 seconds', 'SNGS-055': 'Time: 7.81090 seconds', 'SNGS-080': 'Time: 7.64505 seconds', 'SNGS-105': 'Time: 7.76732 seconds', 'SNGS-130': 'Time: 7.75459 seconds', 'SNGS-155': 'Time: 7.70917 seconds', 'SNGS-196': 'Time: 7.85640 seconds', 'SNGS-031': 'Time: 7.83174 seconds', 'SNGS-056': 'Time: 7.85632 seconds', 'SNGS-081': 'Time: 7.81362 seconds', 'SNGS-106': 'Time: 7.64739 seconds', 'SNGS-131': 'Time: 7.64069 seconds', 'SNGS-156': 'Time: 7.66925 seconds', 'SNGS-197': 'Time: 7.70116 seconds', 'SNGS-032': 'Time: 7.65882 seconds', 'SNGS-057': 'Time: 7.76666 seconds', 'SNGS-082': 'Time: 7.75548 seconds', 'SNGS-107': 'Time: 7.68758 seconds', 'SNGS-132': 'Time: 7.76520 seconds', 'SNGS-157': 'Time: 7.74004 seconds', 'SNGS-198': 'Time: 7.66763 seconds', 'SNGS-033': 'Time: 7.74674 seconds', 'SNGS-058': 'Time: 7.76920 seconds', 'SNGS-083': 'Time: 7.70505 seconds', 'SNGS-108': 'Time: 7.61110 seconds', 'SNGS-133': 'Time: 7.54901 seconds', 'SNGS-158': 'Time: 7.72075 seconds', 'SNGS-199': 'Time: 7.73067 seconds', 'SNGS-034': 'Time: 7.74700 seconds', 'SNGS-059': 'Time: 7.67023 seconds', 'SNGS-084': 'Time: 7.85799 seconds', 'SNGS-109': 'Time: 7.77712 seconds', 'SNGS-134': 'Time: 7.77138 seconds', 'SNGS-159': 'Time: 7.68385 seconds', 'SNGS-200': 'Time: 7.74481 seconds', 'SNGS-035': 'Time: 7.74409 seconds', 'SNGS-060': 'Time: 7.81704 seconds', 'SNGS-085': 'Time: 7.60372 seconds', 'SNGS-110': 'Time: 7.78825 seconds', 'SNGS-135': 'Time: 7.75259 seconds', 'SNGS-160': 'Time: 7.70526 seconds', 'SNGS-036': 'Time: 7.61311 seconds', 'SNGS-061': 'Time: 7.67696 seconds', 'SNGS-086': 'Time: 7.60756 seconds', 'SNGS-111': 'Time: 7.67164 seconds', 'SNGS-136': 'Time: 7.62853 seconds', 'SNGS-161': 'Time: 7.71165 seconds', 'SNGS-037': 'Time: 7.66742 seconds', 'SNGS-062': 'Time: 7.81041 seconds', 'SNGS-087': 'Time: 7.67381 seconds', 'SNGS-112': 'Time: 7.77543 seconds', 'SNGS-137': 'Time: 7.73467 seconds', 'SNGS-162': 'Time: 7.67571 seconds', 'SNGS-038': 'Time: 7.84485 seconds', 'SNGS-063': 'Time: 7.69164 seconds', 'SNGS-088': 'Time: 7.86766 seconds', 'SNGS-113': 'Time: 7.65431 seconds', 'SNGS-138': 'Time: 7.77474 seconds', 'SNGS-163': 'Time: 7.92193 seconds', 'SNGS-039': 'Time: 7.87692 seconds', 'SNGS-064': 'Time: 7.61361 seconds', 'SNGS-089': 'Time: 7.76200 seconds', 'SNGS-114': 'Time: 7.85500 seconds', 'SNGS-139': 'Time: 7.88412 seconds', 'SNGS-164': 'Time: 7.85846 seconds', 'SNGS-040': 'Time: 7.79479 seconds', 'SNGS-065': 'Time: 7.66055 seconds', 'SNGS-090': 'Time: 7.82915 seconds', 'SNGS-115': 'Time: 7.77152 seconds', 'SNGS-140': 'Time: 7.78682 seconds', 'SNGS-165': 'Time: 7.81778 seconds', 'SNGS-041': 'Time: 7.61622 seconds', 'SNGS-066': 'Time: 7.80007 seconds', 'SNGS-091': 'Time: 7.75818 seconds', 'SNGS-116': 'Time: 7.79474 seconds', 'SNGS-141': 'Time: 7.64465 seconds', 'SNGS-166': 'Time: 7.80525 seconds', 'SNGS-042': 'Time: 7.61798 seconds', 'SNGS-067': 'Time: 7.81692 seconds', 'SNGS-092': 'Time: 7.74206 seconds', 'SNGS-117': 'Time: 7.79307 seconds', 'SNGS-142': 'Time: 7.77182 seconds', 'SNGS-167': 'Time: 7.85755 seconds', 'SNGS-043': 'Time: 7.73254 seconds', 'SNGS-068': 'Time: 7.58240 seconds', 'SNGS-093': 'Time: 7.62069 seconds', 'SNGS-118': 'Time: 7.64513 seconds', 'SNGS-143': 'Time: 7.64324 seconds', 'SNGS-168': 'Time: 7.77308 seconds', 'SNGS-044': 'Time: 7.70615 seconds', 'SNGS-069': 'Time: 7.78808 seconds', 'SNGS-094': 'Time: 7.72509 seconds', 'SNGS-119': 'Time: 7.74062 seconds', 'SNGS-144': 'Time: 7.76303 seconds', 'SNGS-169': 'Time: 7.80842 seconds', 'SNGS-045': 'Time: 7.64622 seconds', 'SNGS-070': 'Time: 7.79142 seconds', 'SNGS-095': 'Time: 7.76415 seconds', 'SNGS-120': 'Time: 7.78049 seconds', 'SNGS-145': 'Time: 7.64227 seconds', 'SNGS-170': 'Time: 7.80274 seconds'}
	execution_times = extract_times_dict_as_float_seconds(raw)
	times = np.array(list(execution_times.values()))
	avg_time = np.mean(times)
	std_time = np.std(times)
	print(f"Average prediction time: {avg_time:.4f} seconds")
	print(f"Standard deviation: {std_time:.4f} seconds")
	

def parse_jar_pass_output_times(jar_stdout: str) -> Dict[str, float]:
	"""Per-clip time in seconds from PassPrediction's "split/SNGS-xxx -> N ms" lines."""
	pattern = re.compile(r"(SNGS-\d+)\s*->\s*(\d+(?:\.\d+)?)\s*ms")
	return {m.group(1): float(m.group(2)) / 1000 for m in pattern.finditer(jar_stdout)}


def my_exec_time(times: Dict[str, float]) -> None:
	"""Mean and std of the per-clip times; the first clip also pays for JVM warm-up."""
	values = np.array(list(times.values()))
	print(f"Average time per clip over {len(values)} clips: {np.mean(values):.4f} seconds "
		  f"(std {np.std(values):.4f} s)")
	print(f"Without the first clip (JVM warm-up, {values[0]:.3f} s): {np.mean(values[1:]):.4f} seconds "
		  f"(std {np.std(values[1:]):.4f} s)")


def extract_pass_frame_ranges(
	results_spotting_path: str | Path,
	fps: float = 25.0,
) -> List[int]:
	"""
	Extract detected PASS start frames from SoccerNet spotting JSON output.

	Args:
		results_spotting_path: Path to results_spotting.json.
		fps: Video frame rate used to convert milliseconds to frame index.

	Returns:
		List of frame_start for all PASS detections.
	"""
	path = Path(results_spotting_path)
	with path.open("r", encoding="utf-8") as f:
		payload = json.load(f)

	start_frames: List[int] = []
	for pred in payload.get("predictions", []):
		if pred.get("label") != "PASS":
			continue
		position_ms = float(pred["position"])
		frame_start = int(round((position_ms / 1000.0) * fps))
		start_frames.append(frame_start)

	return start_frames


def extract_passes_for_all_sngs_folders(
	comparison_root: str | Path,
	fps: float = 25.0,
) -> Dict[str, List[int]]:
	"""
	Find all SNGS-xxx folders and extract PASS start frames from each.

	Args:
		comparison_root: Folder containing SNGS-* subfolders.
		fps: Video frame rate used to convert milliseconds to frame index.

	Returns:
		Mapping {"SNGS-xxx": [pass_start_frames]} for folders with a
		`results_spotting.json` file.
	"""
	root = Path(comparison_root)
	folder_pattern = re.compile(r"^SNGS-\d+$")
	results: Dict[str, List[int]] = {}

	for child in sorted(root.iterdir()):
		if not child.is_dir() or not folder_pattern.match(child.name):
			continue

		results_path = child / "results_spotting.json"
		if not results_path.exists():
			continue

		results[child.name] = extract_pass_frame_ranges(results_path, fps=fps)

	return results


def run_experiment_jar(
	jar_path: str | Path = "target/GeometricPatternMatching-1.0-exp-pass.jar",
	jar_args: List[str] | None = None,
	working_dir: str | Path | None = None,
) -> subprocess.CompletedProcess[str]:
	"""
	Run the experiment JAR and return the completed process.

	Args:
		jar_path: Path to the JAR file.
		jar_args: Optional arguments passed to the JAR main class.
		working_dir: Optional working directory for the Java process.

	Returns:
		A subprocess.CompletedProcess with stdout/stderr.

	Raises:
		FileNotFoundError: If the JAR does not exist.
		subprocess.CalledProcessError: If Java process exits with non-zero code.
	"""
	args = jar_args or []
	jar = Path(jar_path)
	if not jar.is_absolute():
		base = Path(working_dir) if working_dir is not None else Path(__file__).resolve().parents[3]
		jar = (base / jar).resolve()

	if not jar.exists():
		raise FileNotFoundError(f"JAR not found: {jar}")

	command = ["java", "-jar", str(jar), *args]
	return subprocess.run(
		command,
		check=True,
		capture_output=True,
		text=True,
		cwd=str(working_dir) if working_dir is not None else str(Path(__file__).resolve().parents[3]),
	)


def parse_jar_pass_output_start_frames(jar_stdout: str) -> Dict[str, List[int]]:
	"""
	Parse experiment JAR stdout into {"SNGS-xxx": [pass_start_frames]}.

	This mirrors the structure used by JSON extraction to make comparisons easy.
	"""
	instance_pattern = re.compile(r"INSTANCE\s*:\s*(SNGS-\d+)")
	frame_pattern = re.compile(r"start\s*=\s*(\d+)")

	results: Dict[str, List[int]] = {}
	current_instance: str | None = None

	for line in jar_stdout.splitlines():
		instance_match = instance_pattern.search(line)
		if instance_match:
			current_instance = instance_match.group(1)
			results.setdefault(current_instance, [])
			continue

		if current_instance is None:
			continue

		frame_match = frame_pattern.search(line)
		if frame_match:
			results[current_instance].append(int(frame_match.group(1)))

	return results


def parse_jar_pass_output_detailed(jar_stdout: str) -> Dict[str, List[Dict[str, int]]]:
	"""
	Parse experiment JAR stdout into detailed PASS events per instance.

	Output shape:
	{
		"SNGS-169": [
			{"from": 3, "to": 15, "start": 200, "length": 118, "end": 318},
			...
		],
		...
	}
	"""
	instance_pattern = re.compile(r"INSTANCE\s*:\s*(SNGS-\d+)")
	event_pattern = re.compile(
		r"From\s+player\s+ID\s+(\d+)\s+to\s+player\s+ID\s+(\d+)\s+\|\s+frames:\s+present\s+start\s*=\s*(\d+)\s+length\s*=\s*(\d+)\s+end\s*=\s*(\d+)"
	)

	results: Dict[str, List[Dict[str, int]]] = {}
	current_instance: str | None = None

	for line in jar_stdout.splitlines():
		instance_match = instance_pattern.search(line)
		if instance_match:
			current_instance = instance_match.group(1)
			results.setdefault(current_instance, [])
			continue

		if current_instance is None:
			continue

		event_match = event_pattern.search(line)
		if event_match:
			from_id, to_id, start, length, end = map(int, event_match.groups())
			results[current_instance].append(
				{
					"from": from_id,
					"to": to_id,
					"start": start,
					"length": length,
					"end": end,
				}
			)

	return results


def run_experiment_jar_and_extract_passes(
	jar_path: str | Path = "target/GeometricPatternMatching-1.0-exp-pass.jar",
	jar_args: List[str] | None = None,
	working_dir: str | Path | None = None,
) -> Dict[str, List[int]]:
	"""Run the JAR and directly return {"SNGS-xxx": [pass_start_frames]} output."""
	completed = run_experiment_jar(jar_path=jar_path, jar_args=jar_args, working_dir=working_dir)
	return parse_jar_pass_output_start_frames(completed.stdout)


def compare_detection_lists_with_tolerance(
	reference_frames: List[int],
	predicted_frames: List[int],
	tolerance: int = 13,
) -> Dict[str, object]:
	"""
	Compare two frame lists with one-to-one matching under +/- tolerance.

	Returns match counts and unmatched frames on each side.
	"""
	ref = sorted(reference_frames)
	pred = sorted(predicted_frames)

	i, j = 0, 0
	matches = 0
	matched_pairs: List[tuple[int, int]] = []
	unmatched_ref: List[int] = []
	unmatched_pred: List[int] = []

	while i < len(ref) and j < len(pred):
		if abs(ref[i] - pred[j]) <= tolerance:
			matches += 1
			matched_pairs.append((ref[i], pred[j]))
			i += 1
			j += 1
		elif ref[i] < pred[j] - tolerance:
			unmatched_ref.append(ref[i])
			i += 1
		else:
			unmatched_pred.append(pred[j])
			j += 1

	if i < len(ref):
		unmatched_ref.extend(ref[i:])
	if j < len(pred):
		unmatched_pred.extend(pred[j:])

	return {
		"matches": matches,
		"reference_total": len(ref),
		"predicted_total": len(pred),
		"reference_unmatched": len(unmatched_ref),
		"predicted_unmatched": len(unmatched_pred),
		"reference_unmatched_frames": unmatched_ref,
		"predicted_unmatched_frames": unmatched_pred,
		"matched_pairs": matched_pairs,
	}


def compare_detection_dictionaries(
	reference: Dict[str, List[int]],
	predicted: Dict[str, List[int]],
	tolerance: int = 13,
) -> Dict[str, object]:
	"""Compare two {"SNGS-xxx": [frames]} dictionaries with frame tolerance."""
	all_instances = sorted(set(reference.keys()) | set(predicted.keys()))
	per_instance: Dict[str, Dict[str, object]] = {}

	total_reference = 0
	total_predicted = 0
	total_matches = 0
	total_reference_unmatched = 0
	total_predicted_unmatched = 0

	for instance in all_instances:
		ref_frames = reference.get(instance, [])
		pred_frames = predicted.get(instance, [])
		stats = compare_detection_lists_with_tolerance(ref_frames, pred_frames, tolerance=tolerance)
		per_instance[instance] = stats

		total_reference += int(stats["reference_total"])
		total_predicted += int(stats["predicted_total"])
		total_matches += int(stats["matches"])
		total_reference_unmatched += int(stats["reference_unmatched"])
		total_predicted_unmatched += int(stats["predicted_unmatched"])

	return {
		"tolerance": tolerance,
		"total": {
			"reference_total": total_reference,
			"predicted_total": total_predicted,
			"matches": total_matches,
			"reference_unmatched": total_reference_unmatched,
			"predicted_unmatched": total_predicted_unmatched,
		},
		"per_instance": per_instance,
	}

def print_comparison_summary(report: Dict[str, object]) -> None:
	t = report["total"]
	tol = report["tolerance"]

	json_total = int(t["reference_total"])
	jar_total = int(t["predicted_total"])
	matches = int(t["matches"])
	json_only = int(t["reference_unmatched"])
	jar_only = int(t["predicted_unmatched"])

	def pct(part: int, whole: int) -> float:
		return (100.0 * part / whole) if whole > 0 else 0.0

	print(f"The JSON detections contain {json_total} pass starts in total.")
	print(f"The JAR detections contain {jar_total} pass starts in total.")
	print(
		f"{matches} detections match between the two methods "
		f"(within the +/-{tol}-frame tolerance, {pct(matches, json_total):.1f}% of JSON and {pct(matches, jar_total):.1f}% of JAR)."
	)
	print(
		f"{json_only} detections appear only in JSON "
		f"(no matching JAR detection, {pct(json_only, json_total):.1f}% of JSON)."
	)
	print(
		f"{jar_only} detections appear only in JAR "
		f"(no matching JSON detection, {pct(jar_only, jar_total):.1f}% of JAR)."
	)


if __name__ == "__main__":
	  
	default_comparison_root = (
		Path(__file__).resolve().parents[3]
		/ "data"
		/ "SoccerNet"
		/ "gamestate-2024"
		/ "comparison"
	)

	# output of PassPrediction, run on the VM (java -cp ... org.main.sn.experiments.PassPrediction > passes_vm.txt)
	parser = argparse.ArgumentParser(description="Compare TactiCP's pass detections with the ML model's.")
	parser.add_argument("--passes", type=Path,
						default=Path(__file__).resolve().parents[3] / "data" / "comparison" / "passes_vm.txt")
	args = parser.parse_args()
	pass_output = args.passes.read_text(encoding="utf-8")

	all_results = extract_passes_for_all_sngs_folders(default_comparison_root, fps=25)

	result_jar = parse_jar_pass_output_start_frames(pass_output)
	report = compare_detection_dictionaries(all_results, result_jar)
	print_comparison_summary(report)

	predict_exec_times()
	my_exec_time(parse_jar_pass_output_times(pass_output))
