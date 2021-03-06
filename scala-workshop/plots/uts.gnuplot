set terminal epslatex color size 8.1cm,5.2cm linewidth 1
set output 'uts.tex'

# Too broad: the graph itself stops at margins.
# set lmargin at screen 0.13
# set rmargin at screen 0.97

set key inside top right box

set style line 1 lt 1 lc rgb "blue"  lw 2
set style line 2 lt 2 lc rgb "red"   lw 2
set style line 3 lt 3 lc rgb "green" lw 2

set xlabel "Number of workers"
set ylabel "Mn/s/worker" offset 2

set grid

plot "uts.dat" using 1:(($5+$6+$7)/(3*$1)):xtic(1) ls 1 title '\small{APGAS}' with lp,\
     "uts.dat" using 1:(($17+$18+$19)/(3*$1)) ls 2 title '\small{Akka}' with lp

#     "uts.dat" using 1:(($14+$15+$16)/(3*$1)) ls 3 title '\small{Akka-pinned}' with lp, \
