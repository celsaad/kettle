import Svg, { Circle, Ellipse, Path, Rect } from 'react-native-svg';

type Props = {
  size?: number;
  color?: string;
  steamColor?: string;
};

/** The kettle glyph from the brand design: handle, lid, knob, body, spout, base, and steam curls. */
export function KettleMark({ size = 118, color = '#f6f0e6', steamColor }: Props) {
  const accent = steamColor ?? color;

  return (
    <Svg width={size} height={size} viewBox="0 0 120 120" fill="none">
      <Path d="M13,32 C8,26 14,21 11,15" stroke={accent} strokeWidth={3.2} strokeLinecap="round" fill="none" opacity={0.7} />
      <Path
        d="M22,30 C17,24 23,19 20,13"
        stroke={accent}
        strokeWidth={3.2}
        strokeLinecap="round"
        fill="none"
        opacity={0.45}
      />
      <Path d="M40,42 C42,23 82,23 84,42" stroke={color} strokeWidth={6.5} strokeLinecap="round" fill="none" />
      <Ellipse cx={62} cy={43} rx={24} ry={6.5} fill={color} />
      <Circle cx={62} cy={33} r={5.5} fill={accent} />
      <Path d="M30,71 C30,53 44,45 62,45 C80,45 96,53 96,71 C96,88 80,95 62,95 C44,95 30,88 30,71 Z" fill={color} />
      <Path d="M33,60 L15,45 L21,39 L39,52 Z" fill={color} />
      <Rect x={47} y={93} width={30} height={7} rx={3.5} fill={color} />
    </Svg>
  );
}
