// What metro.transformer.js and jest.yaml-transform.js turn a `.yaml` import into: the file's text.
declare module '*.yaml' {
  const text: string;
  export default text;
}

declare module '*.yml' {
  const text: string;
  export default text;
}
