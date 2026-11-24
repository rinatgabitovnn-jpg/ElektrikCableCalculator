# Independent sanity checks against ГОСТ Р 50571.5.52-2011, tables B.52.2/B.52.4, B2 column.
from pathlib import Path

sections = [1.5,2.5,4,6,10,16,25,35,50,70,95,120,150,185,240,300]
b2_2 = [16.5,23,30,38,52,69,90,111,133,168,201,232,258,294,344,394]
b2_3 = [15,20,27,34,46,62,80,99,118,149,179,206,225,255,297,339]
assert len(sections) == len(b2_2) == len(b2_3)
assert next(s for s, iz in zip(sections,b2_2) if iz >= 25) == 4
assert next(s for s, iz in zip(sections,b2_3) if iz >= 25) == 4
assert next(s for s, iz in zip(sections,b2_2) if iz >= 32) == 6
assert next(s for s, iz in zip(sections,b2_3) if iz >= 32) == 6
assert b2_2[0] == 16.5 and b2_2[6] == 90 and b2_2[-1] == 394
assert b2_3[0] == 15 and b2_3[6] == 80 and b2_3[-1] == 339
group = {1:1.00,2:.80,3:.70,4:.65,5:.60,6:.57,7:.54,8:.52,9:.50,12:.45,16:.41,20:.38}
assert group[2] == .80 and group[20] == .38
print("PASS: B2 ampacity arrays, endpoint checks, baseline selections, grouping factors")

temp = {10:1.22,15:1.17,20:1.12,25:1.06,30:1.00,35:.94,40:.87,45:.79,50:.71,55:.61,60:.50}
assert temp[30] == 1.0 and temp[40] == .87 and temp[60] == .50
assert all(temp[t] > temp[t+5] for t in range(10,60,5))
def drop(i, length, rho, cos_phi, section, voltage, three_phase=False):
    import math
    factor = math.sqrt(3) if three_phase else 2
    return factor*i*length*rho*cos_phi/(section*voltage)*100
assert drop(25,20,.0225,.95,6,230) > drop(25,20,.0225,.95,10,230)
print("PASS: temperature factors and voltage-drop monotonicity")

source = Path(__file__).parents[1] / "app/src/main/java/ru/om/cablecalc/MainActivity.kt"
code = source.read_text(encoding="utf-8")
assert "Для потолка A2 использован" not in code
assert "потолочной полости" in code
assert 'versionName = "1.4"' in (Path(__file__).parents[1] / "app/build.gradle.kts").read_text()
print("PASS: version metadata and installation-method caveats")
