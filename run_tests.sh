#!/bin/bash
run() {
  rm -rf out_$1; python3 ../launcher/tools/make_test_iso.py $1.iso $2 $3 >/dev/null
  echo "=== $1 (base $2 $3)"
  java -jar ../test.jar $1.iso out_$1 > log_$1.txt 2>&1; rc=$?
  grep -E "FALHOU|Exception|plano" log_$1.txt; echo "rc=$rc  checks ok: $(grep -c '^ok:' log_$1.txt)"
  grep '^M ' log_$1.txt | cut -c3- | sort > got_$1.txt
  sort $1.iso.expected > exp_$1.txt
  diff got_$1.txt exp_$1.txt && echo "MANIFESTO IDENTICO"
}
run raw 0
run xgd3 0x2080000
run xgd2 0xFD90000
run lower 0 --lower
echo; ls out_raw/DATA; echo; find out_lower -type f | sort
